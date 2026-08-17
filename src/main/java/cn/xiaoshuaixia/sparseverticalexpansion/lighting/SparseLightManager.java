package cn.xiaoshuaixia.sparseverticalexpansion.lighting;

import cn.xiaoshuaixia.sparseverticalexpansion.network.ExtendedSectionPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveNetwork;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side coordinator for SVE's frozen sparse lighting.
 *
 * <p>The vanilla light engine must not be pointed at extended sections (it would traverse the whole vertical span), so
 * SVE computes light lazily and only for the chunks a player is actually near. A chunk enters the {@code pending} map
 * when an extended block changes (see the chunk set-state mixin) or when it loads with sections that carry no light
 * yet. A per-tick sweep relights pending chunks that have been stable for a short debounce window, so rapid block
 * placement is batched into a single relight instead of re-lighting on every placement; a per-sweep budget keeps large
 * builds from freezing a frame. Once relit the light is cached in the section storage and stays frozen until another
 * block change marks it dirty again.</p>
 *
 * <p>The relight is a three-stage pipeline mirroring vanilla {@code ThreadedLevelLightEngine}: the main thread snapshots
 * the chunk's block states, a single background worker runs the pure propagation ({@link SparseLightEngine#compute}),
 * and the main thread applies + broadcasts the result on a later tick. This keeps the O(section) propagation off the
 * tick thread — the previous synchronous full recompute is what caused the "Can't keep up!" spikes (it is the 1.12.2
 * lighting-update-lag machine recreated).</p>
 */
public final class SparseLightManager {
    /** How many ticks a chunk must stay untouched before it is relit (debounce: batches rapid placement). */
    private static final int DEBOUNCE_TICKS = 2;
    private static final int LIGHT_RADIUS_CHUNKS = 12;
    private static final int MAX_RELIGHTS_PER_SWEEP = 8;

    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/lighting");
    /** Per-dimension map of chunk pos -> the game time it was last marked dirty. */
    private static final Map<ResourceKey<Level>, Map<Long, Long>> PENDING = new ConcurrentHashMap<>();
    /** Chunks currently being computed off-thread (main-thread-only; worker never touches this). */
    private static final Map<ResourceKey<Level>, Set<Long>> IN_FLIGHT = new ConcurrentHashMap<>();
    /** Per-dimension completed relights awaiting main-thread apply. */
    private static final Map<ResourceKey<Level>, Queue<CompletedRelight>> COMPLETED = new ConcurrentHashMap<>();

    /** Background worker for the pure propagation stage. Lazily created; shut down on server stop. */
    private static volatile ExecutorService executor;

    private SparseLightManager() {
    }

    private record CompletedRelight(ChunkPos pos, Map<Long, SparseLightEngine.SectionLight> light) {
    }

    /**
     * Marks the chunk of a changed extended block for a (re)light, but only when that section's layer has the lighting
     * rule enabled (it is off by default, so non-lit regions never enter the pending map).
     */
    public static void markDirty(ServerLevel level, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        int sectionY = SectionPos.blockToSectionCoord(pos.getY());
        if (!lightingEnabled(level, chunkPos, sectionY)) {
            return;
        }
        PENDING.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                .put(chunkPos.toLong(), level.getGameTime());
    }

    /** Detects loaded chunks whose lighting-enabled sections predate the feature and queues their first relight. */
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelChunk chunk = event.getChunk() instanceof LevelChunk levelChunk ? levelChunk : null;
        if (chunk == null) {
            return;
        }
        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null || storage.isEmpty()) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        for (int sectionY : storage.sectionYs()) {
            // Queue both never-lit sections (old saves without light) and sections whose light came from an older
            // algorithm version (so the frozen old result is recomputed once on upgrade).
            if ((!storage.hasLight(sectionY) || storage.isLightStale(sectionY))
                    && lightingEnabled(level, chunkPos, sectionY)) {
                PENDING.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                        .put(chunkPos.toLong(), level.getGameTime());
                return;
            }
        }
    }

    private static boolean lightingEnabled(ServerLevel level, ChunkPos chunkPos, int sectionY) {
        return SveWorldData.get(level)
                .findRegion(level.dimension().location(), chunkPos.x, chunkPos.z, sectionY << 4)
                .flatMap(region -> region.findLayer(sectionY << 4))
                .map(VerticalLayer::rules)
                .map(SimulationRules::lighting)
                .orElse(false);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            applyCompleted(level);
            sweep(level);
        }
    }

    private static void sweep(ServerLevel level) {
        Map<Long, Long> pending = PENDING.get(level.dimension());
        if (pending == null || pending.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        int budget = MAX_RELIGHTS_PER_SWEEP;
        Set<Long> inFlight = IN_FLIGHT.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet());
        Iterator<Map.Entry<Long, Long>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<Long, Long> entry = iterator.next();
            ChunkPos pos = new ChunkPos(entry.getKey());
            if (!isNearAnyPlayer(level, pos)) {
                continue; // player not close yet; stay pending until someone approaches
            }
            if (now - entry.getValue() < DEBOUNCE_TICKS) {
                continue; // still being edited; wait for a quiet window
            }
            if (!inFlight.add(pos.toLong())) {
                continue; // already queued to the worker
            }
            iterator.remove();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                inFlight.remove(pos.toLong());
                continue;
            }
            // Stage 1 (main thread): snapshot the block states so the worker never touches live chunk data.
            SparseLightEngine.Snapshot snapshot = SparseLightEngine.snapshot(chunk);
            if (snapshot == null) {
                inFlight.remove(pos.toLong());
                continue;
            }
            budget--;
            submit(level, pos, snapshot);
        }
    }

    private static void submit(ServerLevel level, ChunkPos pos, SparseLightEngine.Snapshot snapshot) {
        getExecutor().execute(() -> {
            // Stage 2 (worker thread): pure propagation over the immutable snapshot.
            Map<Long, SparseLightEngine.SectionLight> light = SparseLightEngine.compute(snapshot.sections());
            COMPLETED.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>())
                    .add(new CompletedRelight(pos, light));
        });
    }

    /** Stage 3 (main thread): apply completed results and broadcast to tracking players. */
    private static void applyCompleted(ServerLevel level) {
        Queue<CompletedRelight> queue = COMPLETED.get(level.dimension());
        if (queue == null) {
            return;
        }
        Set<Long> inFlight = IN_FLIGHT.get(level.dimension());
        CompletedRelight completed;
        while ((completed = queue.poll()) != null) {
            if (inFlight != null) {
                inFlight.remove(completed.pos().toLong());
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(completed.pos().x, completed.pos().z);
            if (chunk != null && SparseLightEngine.apply(chunk, completed.light())) {
                LOGGER.debug("relit sparse chunk {} and broadcasting light", completed.pos());
                broadcastLight(level, chunk);
            }
        }
    }

    private static boolean isNearAnyPlayer(ServerLevel level, ChunkPos pos) {
        for (ServerPlayer player : level.players()) {
            ChunkPos playerPos = player.chunkPosition();
            if (Math.abs(playerPos.x - pos.x) <= LIGHT_RADIUS_CHUNKS
                    && Math.abs(playerPos.z - pos.z) <= LIGHT_RADIUS_CHUNKS) {
                return true;
            }
        }
        return false;
    }

    private static void broadcastLight(ServerLevel level, LevelChunk chunk) {
        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null) {
            return;
        }
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        for (int sectionY : storage.sectionYs()) {
            if (!storage.hasLight(sectionY)) {
                continue;
            }
            BlockPos origin = new BlockPos(chunk.getPos().getMinBlockX(), sectionY << 4, chunk.getPos().getMinBlockZ());
            PacketDistributor.sendToPlayersTrackingChunk(
                    level,
                    chunk.getPos(),
                    new ExtendedSectionPayload(
                            chunkX,
                            chunkZ,
                            sectionY,
                            SveNetwork.rulesAt(level, origin).mask(),
                            storage.copyStateIds(sectionY),
                            storage.skyLightBytes(sectionY),
                            storage.blockLightBytes(sectionY)));
        }
    }

    /** Shuts down the background worker and discards any queued state when the server stops. */
    public static void onServerStopped(ServerStoppedEvent event) {
        ExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
            executor = null;
        }
        PENDING.clear();
        IN_FLIGHT.clear();
        COMPLETED.clear();
    }

    private static ExecutorService getExecutor() {
        ExecutorService current = executor;
        if (current == null) {
            synchronized (SparseLightManager.class) {
                current = executor;
                if (current == null) {
                    current = Executors.newSingleThreadExecutor(new LightThreadFactory());
                    executor = current;
                }
            }
        }
        return current;
    }

    private static final class LightThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "SVE-LightWorker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }
}
