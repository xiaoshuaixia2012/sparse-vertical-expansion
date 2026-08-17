package cn.xiaoshuaixia.sparseverticalexpansion.client;

import net.minecraft.world.level.Level;

/**
 * Renderer-agnostic entry point for SVE sparse-section lifecycle notifications.
 *
 * <p>Sodium and Embeddium are mutually exclusive renderer replacements, so at most one backend is
 * active at a time; each backend's own {@code isAvailable()} check makes the inactive one a no-op.
 * Routing everything through this class keeps the payload handlers and the chunk-unload path free
 * of per-renderer assumptions, which is the seam reused for future Forge/Fabric renderer forks.</p>
 */
public final class RendererCompat {
    private RendererCompat() {
    }

    public static void syncSection(int chunkX, int sectionY, int chunkZ, boolean renderable, boolean wasNonAir, boolean isNonAir) {
        SodiumCompat.syncSection(chunkX, sectionY, chunkZ, renderable, wasNonAir, isNonAir);
        EmbeddiumCompat.syncSection(chunkX, sectionY, chunkZ, renderable, wasNonAir, isNonAir);
    }

    public static void onSectionRemoved(int x, int y, int z) {
        SodiumCompat.onSectionRemoved(x, y, z);
        EmbeddiumCompat.onSectionRemoved(x, y, z);
    }

    public static void reload(Level level) {
        SodiumCompat.reload(level);
        EmbeddiumCompat.reload(level);
    }
}
