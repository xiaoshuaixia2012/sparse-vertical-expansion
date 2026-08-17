package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium counterpart of the Sodium light injection: overrides {@code ClonedChunkSection#copyLightArray} for
 * extended sections with the cached sparse-section light.
 */
@Pseudo
@Mixin(value = ClonedChunkSection.class, remap = false)
public abstract class EmbeddiumClonedChunkSectionMixin {
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
            // setReturnValue alone does NOT stop the original body; cancel() is what makes the injected value win.
            cir.cancel();
        }
    }
}
