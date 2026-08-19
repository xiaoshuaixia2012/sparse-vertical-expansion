package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.compat.SablePhysicsCompat;
import cn.xiaoshuaixia.sparseverticalexpansion.lighting.SparseLightManager;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveNetwork;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/placement");

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void sve$getExtendedBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        int y = pos.getY();
        if (!chunk.isOutsideBuildHeight(y)) {
            return;
        }
        if (!isStandardY(y)) {
            callback.setReturnValue(Blocks.VOID_AIR.defaultBlockState());
            return;
        }

        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        callback.setReturnValue(storage == null
                ? Blocks.AIR.defaultBlockState()
                : storage.getBlockState(pos.getX(), y, pos.getZ()));
    }

    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void sve$setExtendedBlockState(
            BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> callback) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        int y = pos.getY();
        if (!chunk.isOutsideBuildHeight(y)) {
            return;
        }
        if (!isStandardY(y) || state.hasBlockEntity()) {
            LOGGER.debug("Rejected extended-Y placement of {} at {} (blockEntity={}, standardY={})",
                    state.getBlock(), pos, state.hasBlockEntity(), isStandardY(y));
            callback.setReturnValue(null);
            return;
        }
        if (!state.isAir()
                && chunk.getLevel() instanceof ServerLevel level
                && SveWorldData.get(level)
                        .findRegion(level.dimension().location(), chunk.getPos().x, chunk.getPos().z, y)
                        .isEmpty()) {
            callback.setReturnValue(null);
            return;
        }

        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null) {
            if (state.isAir()) {
                callback.setReturnValue(null);
                return;
            }
            storage = chunk.getData(SveAttachments.EXTENDED_SECTIONS.get());
        }

        int sectionY = SectionPos.blockToSectionCoord(y);
        BlockState previous = storage.setBlockState(
                pos.getX(),
                y,
                pos.getZ(),
                state,
                chunk.getLevel().registryAccess().registryOrThrow(Registries.BIOME));
        if (previous != null) {
            chunk.setUnsaved(true);
            if (chunk.getLevel() instanceof ServerLevel level) {
                SveNetwork.sendBlockUpdate(level, pos, state);
                SparseLightManager.markDirty(level, pos);
                notifySablePhysics(level, chunk, sectionY, pos, previous, state, storage);
            }
        }
        callback.setReturnValue(previous);
    }

    private static void notifySablePhysics(
            ServerLevel level, LevelChunk chunk, int sectionY, BlockPos pos,
            BlockState previous, BlockState state, SparseSectionStorage storage) {
        // Lazy section registration is owned by SablePhysicsChunkTicketManagerMixin (ship-near). The
        // block-change path only mirrors Sable's cheap 7-block handleBlockChange. A null section means
        // the last block was broken and the section was removed; SablePhysicsCompat supplies a fallback.
        SablePhysicsCompat.notifyBlockChange(
                level, chunk.getPos().x, sectionY, chunk.getPos().z, storage.getSection(sectionY),
                pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, previous, state);
    }

    private static boolean isStandardY(int y) {
        return y >= ExtendedYRange.STANDARD_MIN_Y && y <= ExtendedYRange.STANDARD_MAX_Y;
    }
}
