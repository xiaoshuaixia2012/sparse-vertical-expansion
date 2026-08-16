package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import cn.xiaoshuaixia.sparseverticalexpansion.client.SveRenderer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ViewArea.class)
abstract class ViewAreaMixin {
    @Shadow @Final protected Level level;
    @Shadow protected int sectionGridSizeY;
    @Shadow protected int sectionGridSizeX;
    @Shadow protected int sectionGridSizeZ;
    @Shadow public SectionRenderDispatcher.RenderSection[] sections;
    @Shadow private int getSectionIndex(int x, int y, int z) { throw new AssertionError(); }

    /** @author SVE @reason Reuse vanilla's fixed-size vertical render grid around extended-Y cameras. */
    @Overwrite
    public void repositionCamera(double viewEntityX, double viewEntityZ) {
        int centerX = Mth.ceil(viewEntityX);
        int centerZ = Mth.ceil(viewEntityZ);
        int minY = sve$visibleMinY();
        for (int x = 0; x < sectionGridSizeX; x++) {
            int spanX = sectionGridSizeX * 16;
            int startX = centerX - 8 - spanX / 2;
            int blockX = startX + Math.floorMod(x * 16 - startX, spanX);
            for (int z = 0; z < sectionGridSizeZ; z++) {
                int spanZ = sectionGridSizeZ * 16;
                int startZ = centerZ - 8 - spanZ / 2;
                int blockZ = startZ + Math.floorMod(z * 16 - startZ, spanZ);
                for (int y = 0; y < sectionGridSizeY; y++) {
                    int blockY = SveRenderer.wrappedOriginY(y, minY, sectionGridSizeY);
                    SectionRenderDispatcher.RenderSection section = sections[getSectionIndex(x, y, z)];
                    BlockPos origin = section.getOrigin();
                    if (blockX != origin.getX() || blockY != origin.getY() || blockZ != origin.getZ()) {
                        section.setOrigin(blockX, blockY, blockZ);
                    }
                }
            }
        }
    }

    /** @author SVE @reason Map dirty extended sections into the camera-relative vanilla render grid. */
    @Overwrite
    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread) {
        int x = Math.floorMod(sectionX, sectionGridSizeX);
        int y = SveRenderer.slotForSection(sectionY, sectionGridSizeY);
        int z = Math.floorMod(sectionZ, sectionGridSizeZ);
        sections[getSectionIndex(x, y, z)].setDirty(reRenderOnMainThread);
    }

    /** @author SVE @reason Allow native section lookup inside the extended camera window. */
    @Overwrite
    @Nullable
    public SectionRenderDispatcher.RenderSection getRenderSectionAt(BlockPos pos) {
        int minSectionY = Math.floorDiv(sve$visibleMinY(), 16);
        int sectionY = Math.floorDiv(pos.getY(), 16);
        if (sectionY < minSectionY || sectionY >= minSectionY + sectionGridSizeY) return null;
        int sectionX = Mth.floorDiv(pos.getX(), 16);
        int sectionZ = Mth.floorDiv(pos.getZ(), 16);
        int x = Mth.positiveModulo(sectionX, sectionGridSizeX);
        int y = SveRenderer.slotForSection(sectionY, sectionGridSizeY);
        int z = Mth.positiveModulo(sectionZ, sectionGridSizeZ);
        SectionRenderDispatcher.RenderSection section = sections[getSectionIndex(x, y, z)];
        BlockPos origin = section.getOrigin();
        return origin.getX() == sectionX * 16
                        && origin.getY() == sectionY * 16
                        && origin.getZ() == sectionZ * 16
                ? section
                : null;
    }

    /** @author SVE @reason Give native occlusion traversal the same extended camera window. */
    @Overwrite
    public LevelHeightAccessor getLevelHeightAccessor() {
        return LevelHeightAccessor.create(sve$visibleMinY(), sectionGridSizeY * 16);
    }

    private int sve$visibleMinY() {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        int cameraY = camera == null ? level.getMinBuildHeight() : camera.getBlockY();
        return SveRenderer.visibleMinY(level, cameraY, sectionGridSizeY);
    }
}
