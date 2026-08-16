package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import cn.xiaoshuaixia.sparseverticalexpansion.client.ClientSparseSections;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.chunk.RenderChunk")
abstract class RenderChunkMixin {
    @Shadow @Final LevelChunk wrapped;
    @Unique @Nullable private Map<Integer, PalettedContainer<BlockState>> sve$sections;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sve$snapshotSparseSections(LevelChunk chunk, CallbackInfo callback) {
        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null) return;
        sve$sections = new HashMap<>();
        for (int sectionY : storage.sectionYs()) {
            BlockPos origin = new BlockPos(chunk.getPos().getMinBlockX(), SectionPos.sectionToBlockCoord(sectionY), chunk.getPos().getMinBlockZ());
            if (ClientSparseSections.rulesAt(chunk.getLevel(), origin).rendering()) {
                sve$sections.put(sectionY, storage.getSection(sectionY).getStates().copy());
            }
        }
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void sve$getSparseBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        if (!wrapped.isOutsideBuildHeight(pos.getY())) return;
        PalettedContainer<BlockState> states = sve$sections == null
                ? null
                : sve$sections.get(SectionPos.blockToSectionCoord(pos.getY()));
        callback.setReturnValue(states == null
                ? Blocks.AIR.defaultBlockState()
                : states.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
    }
}
