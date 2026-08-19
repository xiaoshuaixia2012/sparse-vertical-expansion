package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sable;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sable's physics (Aeronautics ships, balloons, augers, offroad wheels, ...) samples blocks through
 * {@code LevelAccelerator#getBlockState(LevelChunk, BlockPos)}, which reads the vanilla section array
 * directly and returns AIR for any position outside the vanilla build height. That makes every
 * extended-Y sparse section invisible to the physics engine, so ships fall straight through them.
 * For extended Y, delegate to {@code LevelChunk#getBlockState(BlockPos)} so SVE's sparse sections
 * take part in the collision pipeline.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.util.LevelAccelerator", remap = false)
public abstract class SableLevelAcceleratorMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true)
    private void sve$extendedBlockState(LevelChunk chunk, BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (pos.getY() < ExtendedYRange.VANILLA_MIN_Y || pos.getY() > ExtendedYRange.VANILLA_MAX_Y) {
            cir.setReturnValue(chunk.getBlockState(pos));
        }
    }
}
