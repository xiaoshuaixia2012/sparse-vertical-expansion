package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.mixin.client.RenderChunkRegionAccessor;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-side read path for SVE sparse-section light, shared by the vanilla renderer. It mirrors vanilla
 * {@code LevelRenderer#getLightColor}: {@code sky << 20 | block << 4}, with block light floored at the block's own
 * light emission. Returns {@code -1} when the position is not extended or its light has not been computed yet, so the
 * caller can fall back to the previous unlit behaviour (FULL_BRIGHT on the vanilla renderer).
 */
public final class SparseLight {
    private SparseLight() {
    }

    public static int getPackedLight(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        Level world = resolveLevel(level);
        if (world != null) {
            LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
            if (storage == null) {
                return -1;
            }
            int sectionY = SectionPos.blockToSectionCoord(pos.getY());
            DataLayer sky = storage.getSkyLight(sectionY);
            DataLayer block = storage.getBlockLight(sectionY);
            if (sky == null && block == null) {
                return -1;
            }
            int lx = pos.getX() & 15;
            int ly = pos.getY() & 15;
            int lz = pos.getZ() & 15;
            int skyLevel = sky == null ? 15 : sky.get(lx, ly, lz);
            int blockLevel = block == null ? 0 : block.get(lx, ly, lz);
            int emission = state.getLightEmission(level, pos);
            if (blockLevel < emission) {
                blockLevel = emission;
            }
            return skyLevel << 20 | blockLevel << 4;
        }
        // Sodium's LevelSlice / Embeddium's WorldSlice are not a Level nor a RenderChunkRegion, so resolveLevel
        // returns null. But their getBrightness already reads the light we injected through copyLightArray, so the
        // extended light is correct without touching the sparse storage directly.
        int skyLevel = level.getBrightness(LightLayer.SKY, pos);
        int blockLevel = level.getBrightness(LightLayer.BLOCK, pos);
        int emission = state.getLightEmission(level, pos);
        if (blockLevel < emission) {
            blockLevel = emission;
        }
        return skyLevel << 20 | blockLevel << 4;
    }

    private static Level resolveLevel(BlockAndTintGetter level) {
        if (level instanceof Level world) {
            return world;
        }
        if (level instanceof RenderChunkRegion region) {
            return ((RenderChunkRegionAccessor) region).sve$getLevel();
        }
        return null;
    }
}
