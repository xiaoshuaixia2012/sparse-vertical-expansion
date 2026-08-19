package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class ClientSparseSections {
    private static final Map<Long, Entry> CHUNKS = new HashMap<>();
    private static Level currentLevel;

    /**
     * Distinct absolute section Ys of every renderable sparse section. {@code hasRenderableSectionNear} is called once
     * a frame while the camera is inside the vanilla build range, so it must not scan all tracked chunks every frame;
     * this set is rebuilt lazily when tracking changes and queried in O(log n) via a range lookup.
     */
    private static final TreeSet<Integer> renderableSectionYs = new TreeSet<>();
    private static boolean renderableSectionYsDirty = true;

    private ClientSparseSections() {
    }

    public static void track(Level level, ChunkPos pos, SparseSectionStorage storage, int sectionY, SimulationRules rules) {
        ensureLevel(level);
        if (storage.isEmpty()) {
            CHUNKS.remove(pos.toLong());
        } else {
            Entry entry = CHUNKS.computeIfAbsent(pos.toLong(), ignored -> new Entry(pos.x, pos.z, storage, new HashMap<>()));
            if (storage.getSection(sectionY) == null) {
                entry.rulesBySection().remove(sectionY);
            } else {
                entry.rulesBySection().put(sectionY, rules);
            }
        }
        renderableSectionYsDirty = true;
    }

    public static void untrack(Level level, ChunkPos pos) {
        ensureLevel(level);
        CHUNKS.remove(pos.toLong());
        renderableSectionYsDirty = true;
    }

    public static SimulationRules rulesAt(Level level, BlockPos pos) {
        ensureLevel(level);
        Entry entry = CHUNKS.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return entry == null
                ? SimulationRules.DEFAULT
                : entry.rulesBySection().getOrDefault(SectionPos.blockToSectionCoord(pos.getY()), SimulationRules.DEFAULT);
    }

    public static LevelChunkSection sectionAt(Level level, int chunkX, int sectionY, int chunkZ) {
        ensureLevel(level);
        Entry entry = CHUNKS.get(ChunkPos.asLong(chunkX, chunkZ));
        return entry == null ? null : entry.storage().getSection(sectionY);
    }

    public static boolean hasRenderableSectionNear(Level level, int cameraY, int radiusSections) {
        ensureLevel(level);
        refreshRenderableSectionYs();
        int cameraSection = SectionPos.blockToSectionCoord(cameraY);
        return !renderableSectionYs.subSet(cameraSection - radiusSections, cameraSection + radiusSections + 1).isEmpty();
    }

    /** Iterates every renderable tracked sparse section for the current level. */
    public static void forEachRenderableSection(Level level, SectionVisitor visitor) {
        ensureLevel(level);
        for (Entry entry : CHUNKS.values()) {
            for (int sectionY : entry.storage().sectionYs()) {
                if (entry.rulesBySection().getOrDefault(sectionY, SimulationRules.DEFAULT).rendering()) {
                    visitor.accept(entry.chunkX(), sectionY, entry.chunkZ());
                }
            }
        }
    }

    private static void refreshRenderableSectionYs() {
        if (!renderableSectionYsDirty) {
            return;
        }
        renderableSectionYs.clear();
        for (Entry entry : CHUNKS.values()) {
            for (int sectionY : entry.storage().sectionYs()) {
                if (entry.rulesBySection().getOrDefault(sectionY, SimulationRules.DEFAULT).rendering()) {
                    renderableSectionYs.add(sectionY);
                }
            }
        }
        renderableSectionYsDirty = false;
    }

    private static void ensureLevel(Level level) {
        if (currentLevel != level) {
            currentLevel = level;
            CHUNKS.clear();
            renderableSectionYsDirty = true;
        }
    }

    public record Entry(int chunkX, int chunkZ, SparseSectionStorage storage, Map<Integer, SimulationRules> rulesBySection) {
    }

    @FunctionalInterface
    public interface SectionVisitor {
        void accept(int chunkX, int sectionY, int chunkZ);
    }
}
