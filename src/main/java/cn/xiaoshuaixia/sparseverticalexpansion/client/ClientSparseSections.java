package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class ClientSparseSections {
    private static final Map<Long, Entry> CHUNKS = new HashMap<>();
    private static Level currentLevel;

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
    }

    public static void untrack(Level level, ChunkPos pos) {
        ensureLevel(level);
        CHUNKS.remove(pos.toLong());
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
        int cameraSection = SectionPos.blockToSectionCoord(cameraY);
        for (Entry entry : CHUNKS.values()) {
            for (int sectionY : entry.storage().sectionYs()) {
                if (Math.abs(sectionY - cameraSection) <= radiusSections
                        && entry.rulesBySection().getOrDefault(sectionY, SimulationRules.DEFAULT).rendering()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void ensureLevel(Level level) {
        if (currentLevel != level) {
            currentLevel = level;
            CHUNKS.clear();
        }
    }

    public record Entry(int chunkX, int chunkZ, SparseSectionStorage storage, Map<Integer, SimulationRules> rulesBySection) {
    }
}
