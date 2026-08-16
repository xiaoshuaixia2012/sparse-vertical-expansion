package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SetBlockCommand.class)
abstract class SetBlockCommandMixin {
    @Inject(method = "setBlock", at = @At("HEAD"))
    private static void sve$requireConfiguredPosition(
            CommandSourceStack source,
            BlockPos pos,
            BlockInput state,
            SetBlockCommand.Mode mode,
            @Nullable Predicate<BlockInWorld> predicate,
            CallbackInfoReturnable<Integer> callback) throws CommandSyntaxException {
        SveCommandValidation.requireConfigured(source.getLevel(), pos);
    }
}
