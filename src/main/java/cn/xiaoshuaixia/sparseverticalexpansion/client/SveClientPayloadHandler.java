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
        boolean wasNonAir = storage.getSection(sectionY) != null;
        level.setServerVerifiedBlockState(pos, payload.state(), 19);
        boolean isNonAir = storage.getSection(sectionY) != null;
        SimulationRules rules = SimulationRules.fromMask(payload.rulesMask());
        ClientSparseSections.track(
                level,
                chunk.getPos(),
                storage,
                sectionY,
                rules);
        RendererCompat.syncSection(
                chunk.getPos().x, sectionY, chunk.getPos().z, rules.rendering(), wasNonAir, isNonAir);
        markSectionDirty(payload.x(), payload.y(), payload.z());
    }

    public static void markSectionDirty(int blockX, int blockY, int blockZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            // The extended-Y render grid (ViewAreaMixin.repositionCamera) is re-anchored around the camera
            // every frame and marks moved sections dirty on re-origin, so a block update only needs the
            // targeted setSectionDirty. The previous allChanged() full re-render here fired on every
            // visibleMinY boundary crossing (e.g. a camera standing at y=495/496) and froze the frame for
            // ~500-800ms per placement.
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.blockToSectionCoord(blockX),
                    SectionPos.blockToSectionCoord(blockY),
                    SectionPos.blockToSectionCoord(blockZ));
        }
    }
}
