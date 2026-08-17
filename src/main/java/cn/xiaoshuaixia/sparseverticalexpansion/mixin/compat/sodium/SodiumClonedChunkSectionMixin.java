package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies SVE's frozen sparse-section light to Sodium's cloned-section cache. Sodium reads light through
 * {@code ClonedChunkSection#copyLightArray}, which normally asks the vanilla light engine and falls back to a
 * default-15 sky / default-0 block layer for extended sections. For extended Y this injects the cached
 * {@link DataLayer} stored in the chunk attachment instead.
 */
@Pseudo
@Mixin(value = ClonedChunkSection.class, remap = false)
public abstract class SodiumClonedChunkSectionMixin {
    @Inject(method = "copyLightArray", at = @At("HEAD"), cancellable = true)
    private static void sve$copySparseLight(
            Level level, LightLayer type, SectionPos pos, CallbackInfoReturnable<DataLayer> cir) {
        if (SodiumSectionLookup.isVanillaSectionY(pos.getY())) {
            return;
        }
        LevelChunk chunk = level.getChunk(pos.getX(), pos.getZ());
        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null) {
            return;
        }
        DataLayer light = type == LightLayer.SKY ? storage.getSkyLight(pos.getY()) : storage.getBlockLight(pos.getY());
        if (light != null) {
            cir.setReturnValue(light);
            cir.cancel();
        }
    }
}
