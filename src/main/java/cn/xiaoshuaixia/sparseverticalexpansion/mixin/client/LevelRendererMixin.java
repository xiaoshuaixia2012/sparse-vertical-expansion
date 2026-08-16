package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow private ClientLevel level;
    @Shadow @Final private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;
    @Unique private final Map<BlockPos, Long> sve$destructionKeys = new HashMap<>();
    @Unique private final Map<Long, BlockPos> sve$destructionPositions = new HashMap<>();
    @Unique private long sve$nextDestructionKey;

    @Redirect(
            method = "compileSections",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;lightOnInSection(Lnet/minecraft/core/SectionPos;)Z"))
    private boolean sve$allowSparseSectionCompile(LevelLightEngine engine, SectionPos pos) {
        return engine.lightOnInSection(pos)
                || (pos.minBlockY() < ExtendedYRange.VANILLA_MIN_Y || pos.minBlockY() > ExtendedYRange.VANILLA_MAX_Y)
                        && !level.getChunk(pos.x(), pos.z()).isSectionEmpty(pos.y());
    }

    @Inject(
            method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true)
    private static void sve$extendedLight(
            BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        if (pos.getY() < ExtendedYRange.VANILLA_MIN_Y || pos.getY() > ExtendedYRange.VANILLA_MAX_Y) {
            callback.setReturnValue(LightTexture.FULL_BRIGHT);
        }
    }

    @Redirect(
            method = "destroyBlockProgress",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;asLong()J"))
    private long sve$retainDestructionPosition(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        return sve$destructionKeys.computeIfAbsent(immutable, ignored -> {
            long key = sve$nextDestructionKey++;
            sve$destructionPositions.put(key, immutable);
            return key;
        });
    }

    @Redirect(
            method = {"removeProgress", "renderLevel"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;asLong()J"))
    private long sve$findDestructionPosition(BlockPos pos) {
        return sve$destructionKeys.getOrDefault(pos, Long.MIN_VALUE);
    }

    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;of(J)Lnet/minecraft/core/BlockPos;"))
    private BlockPos sve$restoreDestructionPosition(long key) {
        return sve$destructionPositions.getOrDefault(key, BlockPos.of(key));
    }

    @Inject(method = "removeProgress", at = @At("TAIL"))
    private void sve$forgetDestructionPosition(BlockDestructionProgress progress, CallbackInfo callback) {
        Long key = sve$destructionKeys.get(progress.getPos());
        if (key != null && !destructionProgress.containsKey(key.longValue())) {
            sve$destructionKeys.remove(progress.getPos());
            sve$destructionPositions.remove(key);
        }
    }
}
