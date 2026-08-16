package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FillCommand.class)
abstract class FillCommandMixin {
    @Inject(method = "fillBlocks", at = @At("HEAD"))
    private static void sve$requireConfiguredArea(
            CommandSourceStack source,
            BoundingBox area,
            BlockInput newBlock,
            @Coerce Object mode,
            @Nullable Predicate<BlockInWorld> replacingPredicate,
            CallbackInfoReturnable<Integer> callback) throws CommandSyntaxException {
        SveCommandValidation.requireConfigured(source.getLevel(), area);
    }
}
