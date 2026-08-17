package cn.xiaoshuaixia.sparseverticalexpansion.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reflection-only bridge to the optional Sodium renderer.
 *
 * <p>This class has <em>zero</em> compile-time or runtime dependency on Sodium. Every access goes
 * through reflection, gated only on whether Sodium is present. There is no version whitelist here:
 * compatibility is verified by <em>actually</em> reaching the render section manager and resolving
 * its methods; if a Sodium build changed its internal API, that check throws instead of silently
 * disabling rendering. Hard version boundaries are declared as {@code incompatible} dependencies in
 * neoforge.mods.toml for an earlier, clearer error.</p>
 *
 * <p>All mutating entry points run on the client (render) thread.</p>
 */
public final class SodiumCompat {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/sodium-compat");
    private static final String SODIUM_MOD_ID = "sodium";
    private static final String WORLD_RENDERER_CLASS = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

    private static volatile Boolean available;

    private static Object lastManager;
    private static Method instanceNullable;
    private static Field renderSectionManagerField;
    private static Method onSectionAdded;
    private static Method onSectionRemoved;
    private static Method scheduleRebuild;
    private static Method markGraphDirty;

    private SodiumCompat() {
    }

    public static boolean isAvailable() {
        Boolean result = available;
        if (result == null) {
            synchronized (SodiumCompat.class) {
                result = available;
                if (result == null) {
                    result = detectAvailability();
                    available = result;
                }
            }
        }
        return result;
    }

    private static boolean detectAvailability() {
        try {
            if (!ModList.get().isLoaded(SODIUM_MOD_ID)) {
                return false;
            }
            Class.forName(WORLD_RENDERER_CLASS, false, SodiumCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Registers (or refreshes) the given sparse section with Sodium.
     *
     * @param renderable whether the section's rules allow rendering at all
     * @param wasNonAir  whether a non-air sparse section existed before the update
     * @param isNonAir   whether a non-air sparse section exists after the update
     */
    public static void syncSection(int chunkX, int sectionY, int chunkZ, boolean renderable, boolean wasNonAir, boolean isNonAir) {
        if (!isAvailable()) {
            return;
        }
        switch (SectionLifecycle.decide(renderable, wasNonAir, isNonAir)) {
            case ADD -> onSectionAdded(chunkX, sectionY, chunkZ);
            case REBUILD -> onSectionChanged(chunkX, sectionY, chunkZ);
            case REMOVE -> onSectionRemoved(chunkX, sectionY, chunkZ);
            case NOOP -> {
                // nothing to do
            }
        }
    }

    public static void onSectionAdded(int x, int y, int z) {
        Object manager = manager();
        if (manager == null) {
            return;
        }
        invoke(onSectionAdded, manager, x, y, z, "onSectionAdded");
    }

    public static void onSectionChanged(int x, int y, int z) {
        Object manager = manager();
        if (manager == null) {
            return;
        }
        // Idempotently ensure the section is registered before scheduling a rebuild.
        invoke(onSectionAdded, manager, x, y, z, "onSectionAdded");
        invoke(scheduleRebuild, manager, x, y, z, true, "scheduleRebuild");
    }

    public static void onSectionRemoved(int x, int y, int z) {
        Object manager = manager();
        if (manager == null) {
            return;
        }
        invoke(onSectionRemoved, manager, x, y, z, "onSectionRemoved");
    }

    public static void markGraphDirty() {
        Object manager = manager();
        if (manager == null) {
            return;
        }
        invoke(markGraphDirty, manager, "markGraphDirty");
    }

    /**
     * Called from the Sodium mixin after the render section manager is (re)created. This is also
     * where API compatibility is probed: if this Sodium build cannot be reached or resolved, it
     * throws so the incompatibility surfaces immediately instead of silently dropping rendering.
     */
    public static void reload(Level level) {
        if (!isAvailable() || level == null) {
            return;
        }
        Object manager = currentManager();
        if (manager == null) {
            return;
        }
        lastManager = manager;
        resolveManagerMethods(manager);
        ClientSparseSections.forEachRenderableSection(level, SodiumCompat::onSectionAdded);
    }

    private static Object manager() {
        Object manager = currentManager();
        if (manager == null) {
            return null;
        }
        if (manager != lastManager) {
            lastManager = manager;
            resolveManagerMethods(manager);
        } else if (onSectionAdded == null) {
            resolveManagerMethods(manager);
        }
        return manager;
    }

    private static Object currentManager() {
        try {
            if (instanceNullable == null) {
                instanceNullable = Class.forName(WORLD_RENDERER_CLASS)
                        .getMethod("instanceNullable");
                renderSectionManagerField = Class.forName(WORLD_RENDERER_CLASS)
                        .getDeclaredField("renderSectionManager");
                renderSectionManagerField.setAccessible(true);
            }
            Object renderer = instanceNullable.invoke(null);
            if (renderer == null) {
                // No renderer attached yet (e.g. before a world is loaded); not an error.
                return null;
            }
            return renderSectionManagerField.get(renderer);
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "SVE Sodium compat: unable to reach the Sodium render section manager; this Sodium build is incompatible.", t);
        }
    }

    private static void resolveManagerMethods(Object manager) {
        try {
            Class<?> clazz = manager.getClass();
            onSectionAdded = clazz.getMethod("onSectionAdded", int.class, int.class, int.class);
            onSectionRemoved = clazz.getMethod("onSectionRemoved", int.class, int.class, int.class);
            scheduleRebuild = clazz.getMethod("scheduleRebuild", int.class, int.class, int.class, boolean.class);
            markGraphDirty = clazz.getMethod("markGraphDirty");
        } catch (Throwable t) {
            throw new IllegalStateException(
                    "SVE Sodium compat: RenderSectionManager API is incompatible; this Sodium build cannot render sparse sections.", t);
        }
    }

    private static void invoke(Method method, Object target, Object arg, String name) {
        try {
            method.invoke(target, arg);
        } catch (Throwable t) {
            throw new IllegalStateException("SVE Sodium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, String name) {
        try {
            method.invoke(target);
        } catch (Throwable t) {
            throw new IllegalStateException("SVE Sodium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, int a, int b, int c, String name) {
        try {
            method.invoke(target, a, b, c);
        } catch (Throwable t) {
            throw new IllegalStateException("SVE Sodium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, int a, int b, int c, boolean d, String name) {
        try {
            method.invoke(target, a, b, c, d);
        } catch (Throwable t) {
            throw new IllegalStateException("SVE Sodium compat: " + name + " failed", t);
        }
    }
}
