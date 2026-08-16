package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.neoforge.NeoForgeWorld", remap = false)
abstract class WorldEditNeoForgeWorldMixin {
    @Shadow
    public abstract ServerLevel getWorld();

    @Inject(method = "getMinY", at = @At("HEAD"), cancellable = true, remap = false)
    private void sve$getMinY(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(ExtendedYRange.STANDARD_MIN_Y);
    }

    @Inject(method = "getMaxY", at = @At("HEAD"), cancellable = true, remap = false)
    private void sve$getMaxY(CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(ExtendedYRange.STANDARD_MAX_Y);
    }

    @Inject(method = "setBlock", at = @At("HEAD"), remap = false)
    private <B extends BlockStateHolder<B>> void sve$requireConfigured(
            BlockVector3 position,
            B block,
            SideEffectSet sideEffects,
            CallbackInfoReturnable<Boolean> callback) throws RegionOperationException {
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!SveCommandValidation.isConfigured(getWorld(), pos)) {
            throw new RegionOperationException(
                    "SVE: 坐标 " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " 不在已配置的垂直区域内");
        }
    }
}
