package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.AbstractBufferingExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.util.collection.BlockMap;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.extent.buffer.internal.BatchingExtent", remap = false)
abstract class WorldEditRegionVisitorMixin extends AbstractBufferingExtent {
    @Shadow
    @Final
    private BlockMap<BaseBlock> blockMap;

    protected WorldEditRegionVisitorMixin(Extent extent) {
        super(extent);
    }

    @Inject(method = "setBlock", at = @At("HEAD"), remap = false)
    private <B extends BlockStateHolder<B>> void sve$validateBeforeBuffering(
            BlockVector3 vector,
            B block,
            CallbackInfoReturnable<Boolean> callback)
            throws RegionOperationException {
        Extent extent = getExtent();
        while (extent instanceof AbstractDelegateExtent delegate) {
            extent = delegate.getExtent();
        }
        if (!extent.getClass().getName().equals("com.sk89q.worldedit.neoforge.NeoForgeWorld")) {
            return;
        }
        ServerLevel level;
        try {
            level = (ServerLevel) extent.getClass().getMethod("getWorld").invoke(extent);
        } catch (ReflectiveOperationException exception) {
            throw new RegionOperationException("SVE 无法验证 WorldEdit 区域");
        }
        BlockPos pos = new BlockPos(vector.x(), vector.y(), vector.z());
        if (!SveCommandValidation.isConfigured(level, pos)) {
            blockMap.clear();
            throw new RegionOperationException(
                    "SVE: 坐标 " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " 不在已配置的垂直区域内");
        }
    }
}
