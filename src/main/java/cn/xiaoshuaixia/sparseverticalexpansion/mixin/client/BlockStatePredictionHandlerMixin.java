package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockStatePredictionHandler.class)
abstract class BlockStatePredictionHandlerMixin {
    @Unique
    private final Map<BlockPos, Long> sve$keysByPosition = new HashMap<>();
    @Unique
    private final Map<Long, BlockPos> sve$positionsByKey = new HashMap<>();
    @Unique
    private long sve$nextKey;

    @Redirect(
            method = "retainKnownServerState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;asLong()J"))
    private long sve$retainWidePosition(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        return sve$keysByPosition.computeIfAbsent(immutable, ignored -> {
            long key = sve$nextKey++;
            sve$positionsByKey.put(key, immutable);
            return key;
        });
    }

    @Redirect(
            method = {"updateKnownServerState", "retainSnapshot"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;asLong()J"))
    private long sve$findWidePosition(BlockPos pos) {
        return sve$keysByPosition.getOrDefault(pos, Long.MIN_VALUE);
    }

    @Redirect(
            method = "endPredictionsUpTo",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;of(J)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sve$restoreWidePosition(long key) {
        BlockPos pos = sve$positionsByKey.remove(key);
        if (pos != null) {
            sve$keysByPosition.remove(pos);
            return pos;
        }
        return BlockPos.of(key);
    }
}
