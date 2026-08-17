package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.client.ClientSparseSections;
import cn.xiaoshuaixia.sparseverticalexpansion.client.RendererCompat;
import cn.xiaoshuaixia.sparseverticalexpansion.client.SveClientPayloadHandler;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SvePermissions;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class SveNetwork {
    private SveNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(ExtendedSectionPayload.TYPE, ExtendedSectionPayload.STREAM_CODEC, SveNetwork::handleSection)
                  .playToClient(
                          ExtendedBlockUpdatePayload.TYPE,
                          ExtendedBlockUpdatePayload.STREAM_CODEC,
                          SveNetwork::handleBlockUpdate)
                  .playToClient(OpenRegionEditorPayload.TYPE, OpenRegionEditorPayload.STREAM_CODEC,
                          (payload, context) -> context.enqueueWork(() -> SveClientPayloadHandler.openRegionEditor(payload)))
                .playToServer(CreateRegionLayerPayload.TYPE, CreateRegionLayerPayload.STREAM_CODEC,
                        SveNetwork::handleCreateRegionLayer)
                .playToServer(DeleteRegionLayerPayload.TYPE, DeleteRegionLayerPayload.STREAM_CODEC,
                        SveNetwork::handleDeleteRegionLayer);
    }

    public static void sendBlockUpdate(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        PacketDistributor.sendToPlayersTrackingChunk(
                level,
                level.getChunkAt(pos).getPos(),
                new ExtendedBlockUpdatePayload(pos.getX(), pos.getY(), pos.getZ(), state, rulesAt(level, pos).mask()));
    }

    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        SparseSectionStorage storage = event.getChunk().getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        if (storage == null) {
            return;
        }
        for (int sectionY : storage.sectionYs()) {
            ServerLevel level = (ServerLevel) event.getChunk().getLevel();
            BlockPos sectionOrigin = new BlockPos(event.getPos().getMinBlockX(), sectionY << 4, event.getPos().getMinBlockZ());
            PacketDistributor.sendToPlayer(
                    event.getPlayer(),
                    new ExtendedSectionPayload(
                            event.getPos().x,
                            event.getPos().z,
                            sectionY,
                            rulesAt(level, sectionOrigin).mask(),
                            storage.copyStateIds(sectionY),
                            storage.skyLightBytes(sectionY),
                            storage.blockLightBytes(sectionY)));
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            SparseSectionStorage storage = event.getChunk().getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
            if (storage != null) {
                int chunkX = event.getChunk().getPos().x;
                int chunkZ = event.getChunk().getPos().z;
                for (int sectionY : storage.sectionYs()) {
                    RendererCompat.onSectionRemoved(chunkX, sectionY, chunkZ);
                }
            }
            ClientSparseSections.untrack(level, event.getChunk().getPos());
        }
    }

    public static void openRegionEditor(ServerPlayer player, BlockPos target) {
        SveWorldData data = SveWorldData.get(player.serverLevel());
        int y = target.getY();
        if (!SveInteraction.isExtendedY(y)) {
            y = ExtendedYRange.VANILLA_MAX_Y + 1;
        }
        ExtendedYRange suggestion = ExtendedYRange.aligned(y, y);
        PacketDistributor.sendToPlayer(player, new OpenRegionEditorPayload(
                target.getX() >> 4,
                target.getZ() >> 4,
                suggestion.minY(),
                suggestion.maxY(),
                data.defaultExtendedMaxY(),
                data.revision(),
                data.findRegion(player.serverLevel().dimension().location(), target.getX() >> 4, target.getZ() >> 4)
                        .map(region -> region.layers())
                        .orElse(java.util.List.of())));
    }

    private static void handleSection(ExtendedSectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleSectionOnClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleSectionOnClient(ExtendedSectionPayload payload) {
        Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        LevelChunk chunk = level.getChunk(payload.chunkX(), payload.chunkZ());
        SparseSectionStorage storage = chunk.getData(SveAttachments.EXTENDED_SECTIONS.get());
        boolean wasNonAir = storage.getSection(payload.sectionY()) != null;
        storage.replaceSection(
                payload.sectionY(),
                payload.stateIds(),
                payload.skyLight(),
                payload.blockLight(),
                level.registryAccess().registryOrThrow(Registries.BIOME));
        boolean isNonAir = storage.getSection(payload.sectionY()) != null;
        SimulationRules rules = SimulationRules.fromMask(payload.rulesMask());
        ClientSparseSections.track(
                level, chunk.getPos(), storage, payload.sectionY(), rules);
        RendererCompat.syncSection(
                chunk.getPos().x, payload.sectionY(), chunk.getPos().z, rules.rendering(), wasNonAir, isNonAir);
        SveClientPayloadHandler.markSectionDirty(payload.chunkX() << 4, payload.sectionY() << 4, payload.chunkZ() << 4);
    }

    private static void handleBlockUpdate(ExtendedBlockUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SveClientPayloadHandler.applyBlockUpdate(payload));
    }

    private static void handleCreateRegionLayer(CreateRegionLayerPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!SvePermissions.has(player.createCommandSourceStack(), SvePermissions.REGION_EDIT)
                || player.chunkPosition().x != payload.chunkX()
                || player.chunkPosition().z != payload.chunkZ()) {
            player.sendSystemMessage(Component.translatable("commands.sve.interaction.rejected"), true);
            return;
        }
        try {
            SveWorldData data = SveWorldData.get(player.serverLevel());
            ExtendedYRange range = ExtendedYRange.aligned(payload.minY(), payload.maxY());
            if (range.maxY() > data.defaultExtendedMaxY()) {
                throw new IllegalArgumentException("maximum Y exceeds the saved-world limit");
            }
            data.addLayer(
                    player.serverLevel().dimension().location(),
                    payload.chunkX(),
                    payload.chunkZ(),
                    new VerticalLayer(range, SimulationRules.fromMask(payload.rulesMask())),
                    payload.revision());
            player.sendSystemMessage(Component.translatable(
                    "commands.sve.region.layer_created", range.minY(), range.maxY()), true);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()), true);
        }
    }

    private static void handleDeleteRegionLayer(DeleteRegionLayerPayload payload, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        if (!SvePermissions.has(player.createCommandSourceStack(), SvePermissions.REGION_EDIT)
                || player.chunkPosition().x != payload.chunkX()
                || player.chunkPosition().z != payload.chunkZ()) {
            player.sendSystemMessage(Component.translatable("commands.sve.interaction.rejected"), true);
            return;
        }
        try {
            ServerLevel level = player.serverLevel();
            SveWorldData data = SveWorldData.get(level);
            ExtendedYRange range = new ExtendedYRange(payload.minY(), payload.maxY());
            data.findRegion(level.dimension().location(), payload.chunkX(), payload.chunkZ())
                    .filter(region -> region.layers().stream().anyMatch(layer -> layer.range().equals(range)))
                    .orElseThrow(() -> new IllegalArgumentException("no vertical layer exactly matches that Y range"));
            LevelChunk chunk = level.getChunk(payload.chunkX(), payload.chunkZ());
            SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
            if (storage != null) {
                var occupied = storage.firstNonAir(
                        range.minY(), range.maxY(), chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ());
                if (occupied.isPresent()) {
                    BlockPos pos = occupied.get();
                    player.sendSystemMessage(Component.translatable(
                            "commands.sve.region.not_empty", pos.getX(), pos.getY(), pos.getZ()), true);
                    return;
                }
            }
            data.removeLayer(
                    level.dimension().location(), payload.chunkX(), payload.chunkZ(), range, payload.revision());
            player.sendSystemMessage(Component.translatable(
                    "commands.sve.region.layer_deleted", range.minY(), range.maxY()), true);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()), true);
        }
    }

    public static SimulationRules rulesAt(ServerLevel level, BlockPos pos) {
        return SveWorldData.get(level)
                .findRegion(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4, pos.getY())
                .flatMap(region -> region.findLayer(pos.getY()))
                .map(VerticalLayer::rules)
                .orElse(SimulationRules.DEFAULT);
    }
}
