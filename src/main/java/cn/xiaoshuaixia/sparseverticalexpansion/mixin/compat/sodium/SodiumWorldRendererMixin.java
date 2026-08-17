package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.RendererCompat;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After Sodium (re)creates its render section manager (world join, render-distance change, reload),
 * re-register every SVE sparse section currently tracked on the client so their geometry survives
 * the manager lifecycle. This runs on the render thread, inside Sodium's own init path.
 */
@Pseudo
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "initRenderer", at = @At("TAIL"))
    private void sve$reRegisterSparseSections(CommandList commandList, CallbackInfo ci) {
        RendererCompat.reload(this.level);
    }
}
