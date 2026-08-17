package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.embeddedt.embeddium.impl.render.chunk.ChunkUpdateType;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionInfo;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes Embeddium register SVE sparse sections as real {@link RenderSection}s at arbitrary section
 * Y. Embeddium 1.0.x keeps the pre-0.8 Sodium section model (no renderable-tree, no isOutOfGraph),
 * and its {@code scheduleRebuild} has no {@code isBuilt()} guard, so only the section-data read in
 * {@code onSectionAdded} needs to be redirected for extended Y.
 */
@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class EmbeddiumRenderSectionManagerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    @Final
    private RenderRegionManager regions;

    @Shadow
    @Final
    private ClientLevel world;

    @Shadow
    private boolean needsUpdate;

    @Shadow
    private void updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        throw new AssertionError("shadowed");
    }

    @Shadow
    private void connectNeighborNodes(RenderSection render) {
        throw new AssertionError("shadowed");
    }

    @Inject(method = "onSectionAdded", at = @At("HEAD"), cancellable = true)
    private void sve$onSparseSectionAdded(int x, int y, int z, CallbackInfo ci) {
        if (SodiumSectionLookup.isVanillaSectionY(y)) {
            return;
        }
        ci.cancel();
        long key = SectionPos.asLong(x, y, z);
        if (this.sectionByPosition.containsKey(key)) {
            return;
        }
        RenderRegion region = this.regions.createForChunk(x, y, z);
        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);
        this.sectionByPosition.put(key, renderSection);

        LevelChunk chunk = this.world.getChunk(x, z);
        LevelChunkSection section = SodiumSectionLookup.sparseSection(chunk, y);
        if (section == null || section.hasOnlyAir()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }
        this.connectNeighborNodes(renderSection);
        this.needsUpdate = true;
    }
}
