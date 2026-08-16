package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void sve$getExtendedBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        Level level = (Level) (Object) this;
        if (!level.isOutsideBuildHeight(pos)) {
            return;
        }
        if (!isStandardY(pos.getY())) {
            callback.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }
        callback.setReturnValue(level.getChunkAt(pos).getBlockState(pos));
    }

    @Redirect(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isOutsideBuildHeight(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean sve$allowExtendedBlock(Level level, BlockPos pos) {
        return level.isOutsideBuildHeight(pos) && !isStandardY(pos.getY());
    }

    private static boolean isStandardY(int y) {
        return y >= ExtendedYRange.STANDARD_MIN_Y && y <= ExtendedYRange.STANDARD_MAX_Y;
    }
}
