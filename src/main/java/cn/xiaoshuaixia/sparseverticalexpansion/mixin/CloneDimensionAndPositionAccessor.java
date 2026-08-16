package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.commands.CloneCommands$DimensionAndPosition")
public interface CloneDimensionAndPositionAccessor {
    @Accessor("dimension")
    ServerLevel sve$dimension();

    @Accessor("position")
    BlockPos sve$position();
}
