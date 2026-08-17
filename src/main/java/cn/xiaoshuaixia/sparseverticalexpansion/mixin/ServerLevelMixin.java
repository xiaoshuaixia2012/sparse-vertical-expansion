package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    /**
     * Vanilla POI (point-of-interest) storage is a {@code SectionStorage} bounded by the
     * vanilla build range. Placing a POI block (bed, bell, workstation, ...) at an extended
     * Y drives {@link PoiManager#add} -> {@code SectionStorage#getOrCreate}, which throws
     * {@code IllegalArgumentException: sectionPos out of bounds}. POI tracking only feeds
     * village/raid logic that is inherently vanilla-range, so we drop the whole call for
     * extended Y instead of letting the exception hit the server task loop.
     */
    @Inject(method = "onBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void sve$skipPoiForExtendedY(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callback) {
        if (pos.getY() < ExtendedYRange.VANILLA_MIN_Y || pos.getY() > ExtendedYRange.VANILLA_MAX_Y) {
            callback.cancel();
        }
    }
}
