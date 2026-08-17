package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium's cloned-cache only clones the vanilla section array and deliberately skips anything
 * outside the build height. For an extended-Y section this mixin clones the SVE sparse
 * {@link LevelChunkSection} instead, so the mesh builder and neighbor sampling see real data.
 */
@Pseudo
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
public abstract class SodiumClonedChunkSectionCacheMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "clone", at = @At("HEAD"), cancellable = true)
    private void sve$cloneSparse(int x, int y, int z, CallbackInfoReturnable<ClonedChunkSection> cir) {
        if (SodiumSectionLookup.isVanillaSectionY(y)) {
            return;
        }
        cir.cancel();
        LevelChunk chunk = this.level.getChunk(x, z);
        LevelChunkSection section = SodiumSectionLookup.sparseSection(chunk, y);
        cir.setReturnValue(new ClonedChunkSection(this.level, chunk, section, SectionPos.of(x, y, z)));
    }
}
