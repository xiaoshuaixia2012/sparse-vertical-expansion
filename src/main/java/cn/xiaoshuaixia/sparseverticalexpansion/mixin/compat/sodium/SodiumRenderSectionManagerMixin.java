package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.RemovableMultiForest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Sodium register SVE sparse sections as real {@link RenderSection}s at arbitrary section Y,
 * and switches the camera-outside-vanilla-range case to the {@code renderableSectionTree}
 * traversal instead of the neighbor-graph occlusion culler (which cannot reach the disconnected
 * sparse islands). The tree traversal is frustum-culled; its distance bound is effectively unset by
 * Sodium's own {@code RemovableMultiForest.traverse}, which is acceptable here because sparse
 * sections only exist in loaded chunks and the vanilla world simply drops out of the frustum when
 * looking horizontally from extended heights.
 */
@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumRenderSectionManagerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sectionByPosition;

    @Shadow
    @Final
    private RenderRegionManager regions;

    @Shadow
    @Final
    private RemovableMultiForest renderableSectionTree;

    @Shadow
    @Final
    private ClientLevel level;

    @Shadow
    private long lastFrameAtTime;

    @Shadow
    private boolean updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        throw new AssertionError("shadowed");
    }

    @Shadow
    private void connectNeighborNodes(RenderSection render) {
        throw new AssertionError("shadowed");
    }

    @Shadow
    public abstract void markGraphDirty();

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

        LevelChunk chunk = this.level.getChunk(x, z);
        LevelChunkSection section = SodiumSectionLookup.sparseSection(chunk, y);
        if (section == null || section.hasOnlyAir()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            this.renderableSectionTree.add(renderSection);
            renderSection.setPendingUpdate(ChunkUpdateTypes.INITIAL_BUILD, this.lastFrameAtTime);
        }
        this.connectNeighborNodes(renderSection);
        this.markGraphDirty();
    }

    @Inject(method = "isOutOfGraph", at = @At("HEAD"), cancellable = true)
    private void sve$extendedOutOfGraph(SectionPos pos, CallbackInfoReturnable<Boolean> cir) {
        int sectionY = pos.getY();
        if (sectionY < this.level.getMinSection() || sectionY >= this.level.getMaxSection()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Sodium's {@code scheduleRebuild} is a no-op for a registered-but-not-yet-built section
     * ({@code isBuilt()} is only set once the first build result is uploaded). A block update that
     * lands in that window is otherwise silently dropped, which is exactly the "fast placement
     * misses a rebuild" failure. For extended-Y sections, re-arm the initial build in that window
     * so the mesh is re-queued and picks up the latest data.
     */
    @Inject(method = "scheduleRebuild", at = @At("HEAD"))
    private void sve$scheduleRebuildUnbuilt(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (SodiumSectionLookup.isVanillaSectionY(y)) {
            return;
        }
        RenderSection section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));
        if (section != null && !section.isBuilt() && section.getPendingUpdate() == 0) {
            section.setPendingUpdate(ChunkUpdateTypes.INITIAL_BUILD, this.lastFrameAtTime);
            this.markGraphDirty();
        }
    }
}
