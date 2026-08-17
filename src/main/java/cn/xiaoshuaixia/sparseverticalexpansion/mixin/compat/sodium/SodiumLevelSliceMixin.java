package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import java.util.List;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Sodium's {@code LevelSlice#prepare} section read for extended-Y origins so a sparse
 * section can become the center of a mesh-build context. This mirrors the vanilla body exactly,
 * including the 2-block neighbor radius (one section in each axis), but pulls the center section
 * from SVE sparse storage and passes an empty mesh-appender list (SVE extended sections hold only
 * plain blocks, never block entities or custom model data).
 *
 * <p>The target is addressed by string so that compiling this class never has to resolve
 * {@code LevelSlice}'s Fabric-API superinterfaces (which are jarjar'd inside Sodium and are not on
 * our compile classpath). The local-section index arithmetic is inlined for the same reason.</p>
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    private static final int NEIGHBOR_CHUNK_RADIUS = 1;
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int SECTION_ARRAY_LENGTH = 1 + NEIGHBOR_CHUNK_RADIUS * 2;
    private static final int SECTION_ARRAY_SIZE = SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH;

    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private static void sve$prepareSparse(
            Level level,
            SectionPos pos,
            ClonedChunkSectionCache cache,
            CallbackInfoReturnable<ChunkRenderContext> cir) {
        if (SodiumSectionLookup.isVanillaSectionY(pos.getY())) {
            return;
        }
        cir.cancel();

        LevelChunk chunk = level.getChunk(pos.getX(), pos.getZ());
        LevelChunkSection center = SodiumSectionLookup.sparseSection(chunk, pos.getY());
        if (center == null || center.hasOnlyAir()) {
            cir.setReturnValue(null);
            return;
        }

        BoundingBox box = new BoundingBox(
                pos.minBlockX() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockY() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockZ() - NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockX() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockY() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockZ() + NEIGHBOR_BLOCK_RADIUS);

        int minChunkX = pos.getX() - NEIGHBOR_CHUNK_RADIUS;
        int minChunkY = pos.getY() - NEIGHBOR_CHUNK_RADIUS;
        int minChunkZ = pos.getZ() - NEIGHBOR_CHUNK_RADIUS;
        int maxChunkX = pos.getX() + NEIGHBOR_CHUNK_RADIUS;
        int maxChunkY = pos.getY() + NEIGHBOR_CHUNK_RADIUS;
        int maxChunkZ = pos.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_ARRAY_SIZE];
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    int localX = chunkX - minChunkX;
                    int localY = chunkY - minChunkY;
                    int localZ = chunkZ - minChunkZ;
                    int index = localY * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH
                            + localZ * SECTION_ARRAY_LENGTH + localX;
                    sections[index] = cache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        cir.setReturnValue(new ChunkRenderContext(pos, sections, box, List.of()));
    }
}
