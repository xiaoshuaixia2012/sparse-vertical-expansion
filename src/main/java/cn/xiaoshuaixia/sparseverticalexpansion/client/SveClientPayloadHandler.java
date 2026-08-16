package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.network.ExtendedBlockUpdatePayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.OpenRegionEditorPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.LevelChunk;

public final class SveClientPayloadHandler {
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int lastVisibleMinY = Integer.MIN_VALUE;
    private SveClientPayloadHandler() {
    }

    public static void openRegionEditor(OpenRegionEditorPayload payload) {
        Minecraft.getInstance().setScreen(new VerticalRegionScreen(payload));
    }

    public static void applyBlockUpdate(ExtendedBlockUpdatePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockPos pos = new BlockPos(payload.x(), payload.y(), payload.z());
        LevelChunk chunk = level.getChunk(payload.x() >> 4, payload.z() >> 4);
        SparseSectionStorage storage = chunk.getData(SveAttachments.EXTENDED_SECTIONS.get());
        int sectionY = SectionPos.blockToSectionCoord(payload.y());
        level.setServerVerifiedBlockState(pos, payload.state(), 19);
        ClientSparseSections.track(
                level,
                chunk.getPos(),
                storage,
                sectionY,
                SimulationRules.fromMask(payload.rulesMask()));
        markSectionDirty(payload.x(), payload.y(), payload.z());
    }

    public static void markSectionDirty(int blockX, int blockY, int blockZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            int cameraY = minecraft.getCameraEntity() == null
                    ? minecraft.level.getMinBuildHeight()
                    : minecraft.getCameraEntity().getBlockY();
            int visibleMinY = SveRenderer.visibleMinY(
                    minecraft.level, cameraY, minecraft.level.getSectionsCount());
            if (lastLevel != minecraft.level || lastVisibleMinY != visibleMinY) {
                lastLevel = minecraft.level;
                lastVisibleMinY = visibleMinY;
                minecraft.levelRenderer.allChanged();
                return;
            }
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.blockToSectionCoord(blockX),
                    SectionPos.blockToSectionCoord(blockY),
                    SectionPos.blockToSectionCoord(blockZ));
        }
    }
}
