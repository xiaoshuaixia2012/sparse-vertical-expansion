package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SvePermissions;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;

public final class SveInteraction {
    private SveInteraction() {
    }

    public static boolean isExtendedY(int y) {
        return y >= ExtendedYRange.STANDARD_MIN_Y
                && y <= ExtendedYRange.STANDARD_MAX_Y
                && (y < ExtendedYRange.VANILLA_MIN_Y || y > ExtendedYRange.VANILLA_MAX_Y);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof BlockItem)) {
            return;
        }
        BlockPos pos = modifiedPos(player, event.getHand(), event.getHitVec());
        if (isExtendedY(pos.getY()) && !canModify(player, pos, true)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isExtendedY(event.getPos().getY())) {
            return;
        }
        if (!canModify(player, event.getPos(), false)) {
            event.setCanceled(true);
            player.connection.send(new ClientboundBlockUpdatePacket(
                    event.getPos(), player.level().getBlockState(event.getPos())));
        }
    }

    public static BlockPos modifiedPos(Player player, InteractionHand hand, BlockHitResult hit) {
        return player.getItemInHand(hand).getItem() instanceof BlockItem
                ? new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit).getClickedPos()
                : hit.getBlockPos();
    }

    private static boolean canModify(ServerPlayer player, BlockPos pos, boolean openEditor) {
        ServerLevel level = player.serverLevel();
        boolean allowed = player.canInteractWithBlock(pos, 1.0)
                && level.mayInteract(player, pos)
                && SvePermissions.has(player.createCommandSourceStack(), SvePermissions.EXTENDED_BUILD);
        boolean hasRegion = SveWorldData.get(level)
                .findRegion(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4, pos.getY())
                .isPresent();
        if (allowed && !hasRegion && openEditor
                && SvePermissions.has(player.createCommandSourceStack(), SvePermissions.REGION_EDIT)) {
            SveNetwork.openRegionEditor(player, pos);
        }
        if (!allowed || !hasRegion) {
            player.sendSystemMessage(Component.translatable("commands.sve.interaction.rejected"), true);
            return false;
        }
        return true;
    }
}
