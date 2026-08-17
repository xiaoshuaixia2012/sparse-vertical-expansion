package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SodiumSectionLookup;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.GraphDirectionSet;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.util.collections.WriteQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium has no renderable-tree fallback: its occlusion culler walks the neighbor graph from the
 * vanilla boundary, so a camera outside the vanilla range never reaches the disconnected sparse
 * islands. When the camera is at extended Y this seeds the traversal with every extended section
 * within render distance and the frustum, mirroring the reachability that vanilla sections get via
 * the contiguous column.
 */
@Pseudo
@Mixin(value = OcclusionCuller.class, remap = false)
public abstract class EmbeddiumOcclusionCullerMixin {
    @Shadow
    @Final
    private Long2ReferenceMap<RenderSection> sections;

    @Shadow
    @Final
    private Level world;

    @Shadow
    private static void visitNode(WriteQueue<RenderSection> queue, RenderSection render, int incoming, int frame) {
        throw new AssertionError("shadowed");
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void sve$initSparseCamera(
            OcclusionCuller.Visitor visitor,
            WriteQueue<RenderSection> queue,
            Viewport viewport,
            float searchDistance,
            boolean useOcclusionCulling,
            int frame,
            CallbackInfo ci) {
        SectionPos origin = viewport.getChunkCoord();
        if (origin.getY() < this.world.getMinSection() || origin.getY() >= this.world.getMaxSection()) {
            ci.cancel();
            int radius = Mth.floor(searchDistance / 16.0F);
            for (RenderSection section : this.sections.values()) {
                if (SodiumSectionLookup.isVanillaSectionY(section.getChunkY())) {
                    continue;
                }
                if (Math.abs(section.getChunkX() - origin.getX()) > radius
                        || Math.abs(section.getChunkY() - origin.getY()) > radius
                        || Math.abs(section.getChunkZ() - origin.getZ()) > radius) {
                    continue;
                }
                if (OcclusionCuller.isWithinFrustum(viewport, section)) {
                    visitNode(queue, section, GraphDirectionSet.ALL, frame);
                }
            }
        }
    }
}
