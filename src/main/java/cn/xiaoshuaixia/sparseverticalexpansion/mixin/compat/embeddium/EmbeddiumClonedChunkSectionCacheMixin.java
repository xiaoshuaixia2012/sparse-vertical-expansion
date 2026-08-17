package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSection;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium's cloned-cache only clones the vanilla section array and skips anything outside the
 * build height. For an extended-Y section this clones the SVE sparse section instead.
 */
@Pseudo
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
public abstract class EmbeddiumClonedChunkSectionCacheMixin {
    @Shadow
    @Final
    private Level world;

    @Inject(method = "clone", at = @At("HEAD"), cancellable = true)
    private void sve$cloneSparse(int x, int y, int z, CallbackInfoReturnable<ClonedChunkSection> cir) {
        if (SodiumSectionLookup.isVanillaSectionY(y)) {
            return;
        }
        cir.cancel();
        LevelChunk chunk = this.world.getChunk(x, z);
        LevelChunkSection section = SodiumSectionLookup.sparseSection(chunk, y);
        cir.setReturnValue(new ClonedChunkSection(this.world, chunk, section, SectionPos.of(x, y, z)));
    }
}
