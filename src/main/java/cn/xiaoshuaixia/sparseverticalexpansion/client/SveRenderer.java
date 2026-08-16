package cn.xiaoshuaixia.sparseverticalexpansion.client;

import net.minecraft.world.level.Level;

public final class SveRenderer {
    private SveRenderer() {
    }

    public static int visibleMinY(Level level, int cameraY, int sectionCount) {
        if (!level.isOutsideBuildHeight(cameraY)
                && !ClientSparseSections.hasRenderableSectionNear(level, cameraY, sectionCount / 2)) {
            return level.getMinBuildHeight();
        }
        return (Math.floorDiv(cameraY, 16) - sectionCount / 2) * 16;
    }

    public static int wrappedOriginY(int slot, int minY, int sectionCount) {
        int span = sectionCount * 16;
        return minY + Math.floorMod(slot * 16 - minY, span);
    }

    public static int slotForSection(int sectionY, int sectionCount) {
        return Math.floorMod(sectionY, sectionCount);
    }
}
