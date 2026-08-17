package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.embeddedt.embeddium.impl.world.cloned.ChunkRenderContext;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSection;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Embeddium's {@code WorldSlice#prepare} section read for extended-Y origins. Mirrors the
 * vanilla body (2-block neighbor radius = one section each axis) but pulls the center section from
 * SVE sparse storage and passes an empty mesh-appender list (SVE extended sections hold only plain
 * blocks). The target is a string and the local-section index is inlined because {@code WorldSlice}
 * implements Fabric-API interfaces that are not on our compile classpath.
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.world.WorldSlice", remap = false)
public abstract class EmbeddiumWorldSliceMixin {
    private static final int NEIGHBOR_CHUNK_RADIUS = 1;
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int SECTION_ARRAY_LENGTH = 1 + NEIGHBOR_CHUNK_RADIUS * 2;
    private static final int SECTION_ARRAY_SIZE = SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH;

    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private static void sve$prepareSparse(
            Level world,
            SectionPos origin,
            ClonedChunkSectionCache sectionCache,
            CallbackInfoReturnable<ChunkRenderContext> cir) {
        if (SodiumSectionLookup.isVanillaSectionY(origin.getY())) {
            return;
        }
        cir.cancel();

        LevelChunk chunk = world.getChunk(origin.getX(), origin.getZ());
        LevelChunkSection center = SodiumSectionLookup.sparseSection(chunk, origin.getY());
        if (center == null || center.hasOnlyAir()) {
            cir.setReturnValue(null);
            return;
        }

        BoundingBox volume = new BoundingBox(
                origin.minBlockX() - NEIGHBOR_BLOCK_RADIUS,
                origin.minBlockY() - NEIGHBOR_BLOCK_RADIUS,
                origin.minBlockZ() - NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockX() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockY() + NEIGHBOR_BLOCK_RADIUS,
                origin.maxBlockZ() + NEIGHBOR_BLOCK_RADIUS);

        int minChunkX = origin.getX() - NEIGHBOR_CHUNK_RADIUS;
        int minChunkY = origin.getY() - NEIGHBOR_CHUNK_RADIUS;
        int minChunkZ = origin.getZ() - NEIGHBOR_CHUNK_RADIUS;
        int maxChunkX = origin.getX() + NEIGHBOR_CHUNK_RADIUS;
        int maxChunkY = origin.getY() + NEIGHBOR_CHUNK_RADIUS;
        int maxChunkZ = origin.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_ARRAY_SIZE];
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    int localX = chunkX - minChunkX;
                    int localY = chunkY - minChunkY;
                    int localZ = chunkZ - minChunkZ;
                    int index = localY * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH
                            + localZ * SECTION_ARRAY_LENGTH + localX;
                    sections[index] = sectionCache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        cir.setReturnValue(new ChunkRenderContext(origin, sections, volume).withMeshAppenders(List.of()));
    }
}
