package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.internal.NeoForgeWorldNativeAccess", remap = false)
abstract class WorldEditNativeAccessMixin {
    @Inject(
            method = "markBlockChanged(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void sve$skipVanillaSectionMark(LevelChunk chunk, BlockPos pos, CallbackInfo callback) {
        cancelExtended(pos, callback);
    }

    @Inject(
            method = "notifyBlockUpdate(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void sve$skipVanillaSectionNotification(
            LevelChunk chunk, BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callback) {
        cancelExtended(pos, callback);
    }

    @Inject(
            method = "updateLightingForBlock(Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void sve$skipUnsupportedSparseLighting(BlockPos pos, CallbackInfo callback) {
        cancelExtended(pos, callback);
    }

    private static void cancelExtended(BlockPos pos, CallbackInfo callback) {
        if (pos.getY() < ExtendedYRange.VANILLA_MIN_Y || pos.getY() > ExtendedYRange.VANILLA_MAX_Y) {
            callback.cancel();
        }
    }
}
