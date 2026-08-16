package cn.xiaoshuaixia.sparseverticalexpansion;

import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveNetwork;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveInteraction;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SvePermissions;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(SparseVerticalExpansion.MOD_ID)
public final class SparseVerticalExpansion {
    public static final String MOD_ID = "sparse_vertical_expansion";

    public SparseVerticalExpansion(IEventBus modEventBus) {
        SveAttachments.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(SveNetwork::register);
        NeoForge.EVENT_BUS.addListener(SveCommands::register);
        NeoForge.EVENT_BUS.addListener(SvePermissions::register);
        NeoForge.EVENT_BUS.addListener(SveNetwork::onChunkSent);
        NeoForge.EVENT_BUS.addListener(SveNetwork::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(SveInteraction::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(SveInteraction::onLeftClickBlock);
    }
}
