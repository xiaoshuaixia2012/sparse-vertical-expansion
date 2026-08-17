package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.RendererCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After Embeddium (re)creates its render section manager (world join, render-distance change,
 * reload), re-register every SVE sparse section currently tracked on the client.
 */
@Pseudo
@Mixin(value = EmbeddiumWorldRenderer.class, remap = false)
public abstract class EmbeddiumWorldRendererMixin {
    @Shadow
    private ClientLevel world;

    @Inject(method = "initRenderer", at = @At("TAIL"))
    private void sve$reRegisterSparseSections(CommandList commandList, CallbackInfo ci) {
        RendererCompat.reload(this.world);
    }
}
