package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
abstract class ChunkHolderMixin {
    @Shadow @Final private LevelHeightAccessor levelHeightAccessor;
    @Shadow @Final private ShortSet[] changedBlocksPerSection;

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void sve$skipExtendedVanillaChangeBuffer(BlockPos pos, CallbackInfo callback) {
        int index = levelHeightAccessor.getSectionIndex(pos.getY());
        if (index < 0 || index >= changedBlocksPerSection.length) {
            callback.cancel();
        }
    }
}
