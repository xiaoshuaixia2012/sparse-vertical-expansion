package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.sk89q.worldedit.util.collection.LocatedBlockList", remap = false)
abstract class WorldEditLocatedBlockListMixin {
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/sk89q/worldedit/util/collection/PositionList;create(Z)Lcom/sk89q/worldedit/util/collection/PositionList;"),
            index = 0,
            remap = false)
    private boolean sve$useWideHistoryPositions(boolean configured) {
        return true;
    }
}
