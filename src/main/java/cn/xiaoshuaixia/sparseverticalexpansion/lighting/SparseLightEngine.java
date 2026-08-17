package cn.xiaoshuaixia.sparseverticalexpansion.lighting;

import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;

/**
 * Sparse, separated (sky + block) light propagation for extended-height sections.
 *
 * <p>This is deliberately <em>not</em> the vanilla {@link net.minecraft.world.level.lighting.LightEngine}: the vanilla
 * engine's column/top-section bookkeeping assumes the immutable vanilla build range, and pointing it at a section tens
 * of thousands of sections above the world triggers an unbounded vertical traversal (the "one step freezes" failure).
 * Instead SVE computes light for the small set of sections that actually exist, on demand, and the result is cached
 * (frozen) by {@link SparseLightManager} rather than re-propagated every tick.</p>
 *
 * <p>The computation is split into three stages so {@link SparseLightManager} can run the expensive part off the server
 * thread, mirroring vanilla's {@code ThreadedLevelLightEngine}:</p>
 * <ol>
 *   <li>{@link #snapshot(LevelChunk)} copies the 3×3 neighbourhood's block states into an immutable snapshot on the
 *       main thread (reading {@code LevelChunkSection} from a worker would race block placement);</li>
 *   <li>{@link #compute(Collection)} runs the pure propagation over the snapshot on any thread and only touches the
 *       snapshot's {@code BlockState[]} arrays and freshly allocated {@code DataLayer}s;</li>
 *   <li>{@link #apply(LevelChunk, Map)} writes the result back to the chunk's storage and must run on the server thread.</li>
 * </ol>
 */
public final class SparseLightEngine {
    public static final int MAX_LEVEL = 15;
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Section Y of the vanilla build-height ceiling (Y=319); sparse sections above this receive sky light. */
    private static final int VANILLA_MAX_SECTION = 19;

    private SparseLightEngine() {
    }

    /**
     * A single 16³ section participating in the propagation, in absolute section coordinates. The block states are a
     * snapshot copy (not the live {@code LevelChunkSection}) so the propagation can run off-thread safely.
     */
    public record Section(int x, int y, int z, BlockState[] states) {
    }

    /** The separated sky and block light result for one section. */
    public record SectionLight(DataLayer sky, DataLayer block) {
    }

    /** A thread-safe snapshot of a chunk neighbourhood, ready for {@link #compute(Collection)} on a worker thread. */
    public record Snapshot(ChunkPos center, Collection<Section> sections) {
    }

    /**
     * Copies the block states of the centre chunk's sparse sections plus its 8 horizontal neighbours into a snapshot.
     * Returns {@code null} when the centre chunk has no lighting-enabled sparse sections (nothing to compute).
     *
     * <p>Must run on the server thread: it reads live {@code LevelChunkSection}s, which are mutated by block placement
     * on that same thread. The copy is cheap (4096 references per section); the expensive propagation is the part this
     * method deliberately does not do.</p>
     */
    @Nullable
    public static Snapshot snapshot(LevelChunk center) {
        SparseSectionStorage centerStorage = center.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (centerStorage == null || centerStorage.isEmpty()) {
            return null;
        }
        Level level = center.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        int centerX = center.getPos().x;
        int centerZ = center.getPos().z;
        boolean anyLit = false;
        for (int sectionY : centerStorage.sectionYs()) {
            if (rulesAt(serverLevel, centerX, centerZ, sectionY).lighting()) {
                anyLit = true;
                break;
            }
        }
        if (!anyLit) {
            return null;
        }

        List<Section> sections = new ArrayList<>();
        for (int chunkX = centerX - 1; chunkX <= centerX + 1; chunkX++) {
            for (int chunkZ = centerZ - 1; chunkZ <= centerZ + 1; chunkZ++) {
                LevelChunk chunk = center.getLevel().getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
                if (storage == null || storage.isEmpty()) {
                    continue;
                }
                for (int sectionY : storage.sectionYs()) {
                    LevelChunkSection section = storage.getSection(sectionY);
                    if (section == null) {
                        continue;
                    }
                    BlockState[] states = new BlockState[4096];
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                states[localIndex(lx, ly, lz)] = section.getBlockState(lx, ly, lz);
                            }
                        }
                    }
                    sections.add(new Section(chunkX, sectionY, chunkZ, states));
                }
            }
        }
        return new Snapshot(center.getPos(), sections);
    }

    /**
     * Computes separated sky and block light for every given section, keyed by {@link SectionPos#asLong}. Non-stored
     * neighbours are treated as air, so propagation is bounded to the supplied sections; cross-chunk horizontal bleed is
     * achieved by the caller supplying the 3×3 chunk neighbourhood.
     *
     * <p>Thread-safe: it only reads the immutable {@link Section#states()} arrays and writes freshly allocated
     * {@code DataLayer}s. The {@code BlockGetter} used for {@code getLightBlock}/{@code getLightEmission} is a snapshot
     * view over those same arrays, so no live chunk data is touched.</p>
     */
    public static Map<Long, SectionLight> compute(Collection<Section> sections) {
        Map<Long, BlockState[]> bySection = new Long2ObjectOpenHashMap<>();
        Map<Long, DataLayer> sky = new Long2ObjectOpenHashMap<>();
        Map<Long, DataLayer> block = new Long2ObjectOpenHashMap<>();
        List<Long> ordered = new ArrayList<>(sections.size());

        for (Section section : sections) {
            long key = SectionPos.asLong(section.x(), section.y(), section.z());
            bySection.put(key, section.states());
            sky.put(key, new DataLayer(0));
            block.put(key, new DataLayer(0));
            ordered.add(key);
        }

        BlockGetter view = new SnapshotView(bySection);
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();
        computeSkyBaseline(view, bySection, sky, tmp);
        Deque<Cell> queue = new ArrayDeque<>();

        // Sky horizontal spread (overhangs/windows): seed every already-lit cell, then flood. A FIFO queue keeps this
        // O(1) per step like vanilla's increase propagation (the previous heap-based queue was what caused the tick
        // overload).
        for (long key : ordered) {
            DataLayer layer = sky.get(key);
            for (int local = 0; local < 4096; local++) {
                int value = layer.get(local & 15, local >>> 4 & 15, local >>> 8 & 15);
                if (value > 1) {
                    queue.add(new Cell(key, local, value));
                }
            }
        }
        propagate(view, bySection, sky, queue, tmp);

        // Block light: seed from emissive blocks, then flood.
        queue.clear();
        for (long key : ordered) {
            BlockState[] states = bySection.get(key);
            DataLayer layer = block.get(key);
            int sx = SectionPos.x(key);
            int sy = SectionPos.y(key);
            int sz = SectionPos.z(key);
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState state = states[localIndex(lx, ly, lz)];
                        tmp.set(sectionToBlock(sx, lx), sectionToBlock(sy, ly), sectionToBlock(sz, lz));
                        int emission = state.getLightEmission(view, tmp);
                        if (emission > 0) {
                            int local = localIndex(lx, ly, lz);
                            layer.set(lx, ly, lz, Math.max(layer.get(lx, ly, lz), emission));
                            queue.add(new Cell(key, local, emission));
                        }
                    }
                }
            }
        }
        propagate(view, bySection, block, queue, tmp);

        Map<Long, SectionLight> result = new Long2ObjectOpenHashMap<>(ordered.size());
        for (long key : ordered) {
            result.put(key, new SectionLight(sky.get(key), block.get(key)));
        }
        return result;
    }

    /**
     * Writes the recomputed light back to the centre chunk only. Returns {@code true} when at least one centre
     * section's light actually changed. Must run on the server thread (it mutates the chunk's attachment storage).
     */
    public static boolean apply(LevelChunk center, Map<Long, SectionLight> light) {
        SparseSectionStorage centerStorage = center.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (centerStorage == null || centerStorage.isEmpty()) {
            return false;
        }
        if (!(center.getLevel() instanceof ServerLevel serverLevel)) {
            return false;
        }
        int centerX = center.getPos().x;
        int centerZ = center.getPos().z;
        boolean changed = false;
        for (Map.Entry<Long, SectionLight> entry : light.entrySet()) {
            long key = entry.getKey();
            if (SectionPos.x(key) != centerX || SectionPos.z(key) != centerZ) {
                continue;
            }
            int sectionY = SectionPos.y(key);
            if (centerStorage.getSection(sectionY) == null
                    || !rulesAt(serverLevel, centerX, centerZ, sectionY).lighting()) {
                continue;
            }
            DataLayer newSky = entry.getValue().sky();
            DataLayer newBlock = entry.getValue().block();
            if (!sameLight(centerStorage.getSkyLight(sectionY), newSky)
                    || !sameLight(centerStorage.getBlockLight(sectionY), newBlock)) {
                centerStorage.setLight(sectionY, newSky, newBlock);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Synchronous convenience: snapshot + compute + apply in one call. Used by GameTests and as a fallback; the live
     * server path goes through {@link SparseLightManager}, which runs {@link #compute(Collection)} off-thread.
     */
    public static boolean relightChunk(LevelChunk center) {
        Snapshot snapshot = snapshot(center);
        if (snapshot == null) {
            return false;
        }
        return apply(center, compute(snapshot.sections()));
    }

    private static boolean sameLight(DataLayer a, DataLayer b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Arrays.equals(a.getData(), b.getData());
    }

    private static SimulationRules rulesAt(ServerLevel level, int chunkX, int chunkZ, int sectionY) {
        return SveWorldData.get(level)
                .findRegion(level.dimension().location(), chunkX, chunkZ, sectionY << 4)
                .flatMap(region -> region.findLayer(sectionY << 4))
                .map(VerticalLayer::rules)
                .orElse(SimulationRules.DEFAULT);
    }

    private static void computeSkyBaseline(
            BlockGetter level, Map<Long, BlockState[]> bySection, Map<Long, DataLayer> sky, BlockPos.MutableBlockPos tmp) {
        // Collect the distinct (sectionX, sectionZ) columns and their sorted section Ys.
        Map<Long, List<Integer>> columns = new Long2ObjectOpenHashMap<>();
        for (long key : bySection.keySet()) {
            columns.computeIfAbsent(SectionPos.getZeroNode(key), ignored -> new ArrayList<>())
                    .add(SectionPos.y(key));
        }
        for (List<Integer> ys : columns.values()) {
            ys.sort(Integer::compareTo);
        }

        for (Map.Entry<Long, List<Integer>> column : columns.entrySet()) {
            int sx = SectionPos.x(column.getKey());
            int sz = SectionPos.z(column.getKey());
            List<Integer> ys = column.getValue();
            // Below (or inside) the vanilla build range there is no sky light: the vanilla bedrock/void blocks it all,
            // so negative-Y sparse sections stay dark unless block-lit.
            if (ys.get(ys.size() - 1) <= VANILLA_MAX_SECTION) {
                continue;
            }
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int opacityAbove = 0;
                    // Walk from the highest section down; once the column is fully blocked, everything below stays 0.
                    for (int i = ys.size() - 1; i >= 0 && opacityAbove < MAX_LEVEL; i--) {
                        int sy = ys.get(i);
                        BlockState[] states = bySection.get(SectionPos.asLong(sx, sy, sz));
                        if (states == null) {
                            continue;
                        }
                        DataLayer layer = sky.get(SectionPos.asLong(sx, sy, sz));
                        if (layer == null) {
                            continue;
                        }
                        for (int ly = 15; ly >= 0; ly--) {
                            BlockState state = states[localIndex(lx, ly, lz)];
                            tmp.set(sectionToBlock(sx, lx), sectionToBlock(sy, ly), sectionToBlock(sz, lz));
                            int value = MAX_LEVEL - opacityAbove;
                            if (value > 0) {
                                layer.set(lx, ly, lz, value);
                            }
                            opacityAbove += state.getLightBlock(level, tmp);
                            if (opacityAbove >= MAX_LEVEL) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void propagate(
            BlockGetter level,
            Map<Long, BlockState[]> bySection,
            Map<Long, DataLayer> out,
            Deque<Cell> queue,
            BlockPos.MutableBlockPos tmp) {
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            DataLayer layer = out.get(cell.section);
            if (layer == null) {
                continue;
            }
            int lx = cell.local & 15;
            int ly = cell.local >>> 4 & 15;
            int lz = cell.local >>> 8 & 15;
            if (layer.get(lx, ly, lz) > cell.level) {
                continue; // stale queue entry
            }
            int sx = SectionPos.x(cell.section);
            int sy = SectionPos.y(cell.section);
            int sz = SectionPos.z(cell.section);

            for (Direction direction : DIRECTIONS) {
                Neighbor neighbor = neighbor(sx, sy, sz, lx, ly, lz, direction);
                DataLayer neighborLayer = out.get(neighbor.section);
                if (neighborLayer == null) {
                    continue;
                }
                BlockState[] neighborStates = bySection.get(neighbor.section);
                if (neighborStates == null) {
                    continue;
                }
                int nlx = neighbor.local & 15;
                int nly = neighbor.local >>> 4 & 15;
                int nlz = neighbor.local >>> 8 & 15;
                BlockState neighborState = neighborStates[localIndex(nlx, nly, nlz)];
                tmp.set(
                        sectionToBlock(SectionPos.x(neighbor.section), nlx),
                        sectionToBlock(SectionPos.y(neighbor.section), nly),
                        sectionToBlock(SectionPos.z(neighbor.section), nlz));
                int newLevel = cell.level - Math.max(1, neighborState.getLightBlock(level, tmp));
                int current = neighborLayer.get(nlx, nly, nlz);
                if (newLevel > current) {
                    neighborLayer.set(nlx, nly, nlz, newLevel);
                    if (newLevel > 1) {
                        queue.add(new Cell(neighbor.section, neighbor.local, newLevel));
                    }
                }
            }
        }
    }

    private static Neighbor neighbor(int sx, int sy, int sz, int lx, int ly, int lz, Direction direction) {
        int nx = lx + direction.getStepX();
        int ny = ly + direction.getStepY();
        int nz = lz + direction.getStepZ();
        int sectionX = sx;
        int sectionY = sy;
        int sectionZ = sz;
        if (nx < 0) {
            sectionX--;
            nx = 15;
        } else if (nx > 15) {
            sectionX++;
            nx = 0;
        }
        if (ny < 0) {
            sectionY--;
            ny = 15;
        } else if (ny > 15) {
            sectionY++;
            ny = 0;
        }
        if (nz < 0) {
            sectionZ--;
            nz = 15;
        } else if (nz > 15) {
            sectionZ++;
            nz = 0;
        }
        return new Neighbor(SectionPos.asLong(sectionX, sectionY, sectionZ), localIndex(nx, ny, nz));
    }

    private static int localIndex(int lx, int ly, int lz) {
        return lx | ly << 4 | lz << 8;
    }

    private static int sectionToBlock(int sectionCoord, int local) {
        return (sectionCoord << 4) + local;
    }

    /**
     * A read-only {@link BlockGetter} over the snapshot sections. It serves {@code getLightBlock}/{@code getLightEmission}
     * from the copied block states, so the whole propagation never touches a live {@code LevelChunk} off-thread.
     */
    private static final class SnapshotView implements BlockGetter {
        private final Map<Long, BlockState[]> sections;

        private SnapshotView(Map<Long, BlockState[]> sections) {
            this.sections = sections;
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState[] states = sections.get(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ())));
            return states == null
                    ? Blocks.AIR.defaultBlockState()
                    : states[(pos.getX() & 15) | ((pos.getY() & 15) << 4) | ((pos.getZ() & 15) << 8)];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return ExtendedYRange.STANDARD_MAX_Y - ExtendedYRange.STANDARD_MIN_Y + 1;
        }

        @Override
        public int getMinBuildHeight() {
            return ExtendedYRange.STANDARD_MIN_Y;
        }
    }

    private static final class Cell {
        private final long section;
        private final int local;
        private final int level;

        private Cell(long section, int local, int level) {
            this.section = section;
            this.local = local;
            this.level = level;
        }

        private int level() {
            return level;
        }
    }

    private record Neighbor(long section, int local) {
    }
}
