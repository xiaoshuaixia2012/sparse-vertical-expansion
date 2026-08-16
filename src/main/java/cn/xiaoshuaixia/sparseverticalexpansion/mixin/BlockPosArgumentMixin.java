package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockPosArgument.class)
abstract class BlockPosArgumentMixin {
    @Redirect(
            method = "getLoadedBlockPos(Lcom/mojang/brigadier/context/CommandContext;Lnet/minecraft/server/level/ServerLevel;Ljava/lang/String;)Lnet/minecraft/core/BlockPos;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isInWorldBounds(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean sve$allowExtendedCommandPosition(Level level, BlockPos pos) {
        return SveCommandValidation.isInCommandBounds(level, pos);
    }
}
