package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CloneCommands.class)
abstract class CloneCommandsMixin {
    @Inject(method = "clone", at = @At("HEAD"))
    private static void sve$requireConfiguredAreas(
            CommandSourceStack source,
            @Coerce Object begin,
            @Coerce Object end,
            @Coerce Object target,
            Predicate<BlockInWorld> filter,
            @Coerce Object mode,
            CallbackInfoReturnable<Integer> callback) throws CommandSyntaxException {
        CloneDimensionAndPositionAccessor from = (CloneDimensionAndPositionAccessor) begin;
        CloneDimensionAndPositionAccessor to = (CloneDimensionAndPositionAccessor) end;
        CloneDimensionAndPositionAccessor destination = (CloneDimensionAndPositionAccessor) target;
        BoundingBox sourceArea = BoundingBox.fromCorners(from.sve$position(), to.sve$position());
        BlockPos targetEnd = destination.sve$position().offset(sourceArea.getLength());
        SveCommandValidation.requireConfigured(
                destination.sve$dimension(), BoundingBox.fromCorners(destination.sve$position(), targetEnd));
        if (((Enum<?>) mode).name().equals("MOVE")) {
            SveCommandValidation.requireConfigured(from.sve$dimension(), sourceArea);
        }
    }
}
