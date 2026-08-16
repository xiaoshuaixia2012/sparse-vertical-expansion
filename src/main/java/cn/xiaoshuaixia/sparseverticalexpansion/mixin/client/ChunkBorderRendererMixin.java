package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SveRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkBorderRenderer.class)
abstract class ChunkBorderRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinBuildHeight()I"))
    private int sve$visibleMinY(ClientLevel level) {
        int y = minecraft.gameRenderer.getMainCamera().getEntity().getBlockY();
        return SveRenderer.visibleMinY(level, y, level.getSectionsCount());
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxBuildHeight()I"))
    private int sve$visibleMaxY(ClientLevel level) {
        int y = minecraft.gameRenderer.getMainCamera().getEntity().getBlockY();
        int minY = SveRenderer.visibleMinY(level, y, level.getSectionsCount());
        // ponytail: F3+G follows the native 24-section window; millions of grid lines per frame are unusable.
        return minY == level.getMinBuildHeight() ? level.getMaxBuildHeight() : minY + level.getSectionsCount() * 16;
    }
}
