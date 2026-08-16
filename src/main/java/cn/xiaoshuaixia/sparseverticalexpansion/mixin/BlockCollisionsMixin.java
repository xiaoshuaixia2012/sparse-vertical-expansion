package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.client.ClientSparseSections;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockCollisions.class)
abstract class BlockCollisionsMixin {
    @Shadow @Final private CollisionGetter collisionGetter;

    @Redirect(
            method = "computeNext",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState sve$applyCollisionRule(BlockGetter getter, BlockPos pos) {
        if (pos.getY() < ExtendedYRange.VANILLA_MIN_Y || pos.getY() > ExtendedYRange.VANILLA_MAX_Y) {
            SimulationRules rules = rulesAt(pos);
            if (!rules.collision()) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return getter.getBlockState(pos);
    }

    private SimulationRules rulesAt(BlockPos pos) {
        if (collisionGetter instanceof ServerLevel level) {
            return SveWorldData.get(level)
                    .findRegion(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4, pos.getY())
                    .flatMap(region -> region.findLayer(pos.getY()))
                    .map(layer -> layer.rules())
                    .orElse(SimulationRules.DEFAULT);
        }
        if (collisionGetter instanceof Level level && level.isClientSide()) {
            return ClientSparseSections.rulesAt(level, pos);
        }
        return SimulationRules.DEFAULT;
    }
}
