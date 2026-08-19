package cn.xiaoshuaixia.sparseverticalexpansion.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reflection-only bridge to the optional Embeddium (铷) renderer — the NeoForge port of Sodium.
 *
 * <p>Embeddium's internal package layout and several member signatures differ from Sodium
 * ({@code org.embeddedt.embeddium.impl.*}, {@code EmbeddiumWorldRenderer}, {@code WorldSlice},
 * {@code ChunkUpdateType} enum), so this bridge is kept separate. The architecture (reflection
 * bridge + optional mixins) is the same, and like {@link SodiumCompat} it has no version whitelist:
 * compatibility is verified by actually reaching the manager and resolving its methods. Hard version
 * boundaries live in neoforge.mods.toml.</p>
 *
 * <p>Reflection failures are <em>non-fatal</em>: a transient failure (renderer not ready yet, or a
 * renderer conflict when both Sodium and Embeddium are installed) logs a warning and degrades to a
 * no-op instead of throwing up into the section-sync payload handler and disconnecting the client. A
 * genuine API mismatch disables this bridge for the session and logs once.</p>
 */
public final class EmbeddiumCompat {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/embeddium-compat");
    private static final String EMBEDDIUM_MOD_ID = "embeddium";
    private static final String WORLD_RENDERER_CLASS = "org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer";

    private static volatile Boolean available;
    private static volatile boolean failureLogged;

    private static Object lastManager;
    private static Method instanceNullable;
    private static Field renderSectionManagerField;
    private static Method onSectionAdded;
    private static Method onSectionRemoved;
    private static Method scheduleRebuild;
    private static Method markGraphDirty;

    private EmbeddiumCompat() {
    }

    public static boolean isAvailable() {
        Boolean result = available;
        if (result == null) {
            synchronized (EmbeddiumCompat.class) {
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
            if (!ModList.get().isLoaded(EMBEDDIUM_MOD_ID)) {
                return false;
            }
            Class.forName(WORLD_RENDERER_CLASS, false, EmbeddiumCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

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

    /** Called from the Embeddium mixin after the render section manager is (re)created. */
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
        ClientSparseSections.forEachRenderableSection(level, EmbeddiumCompat::onSectionAdded);
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
            Method instance = instanceNullable;
            Field field = renderSectionManagerField;
            if (instance == null || field == null) {
                Class<?> clazz = Class.forName(WORLD_RENDERER_CLASS);
                instance = clazz.getMethod("instanceNullable");
                field = clazz.getDeclaredField("renderSectionManager");
                field.setAccessible(true);
                instanceNullable = instance;
                renderSectionManagerField = field;
            }
            Object renderer = instance.invoke(null);
            if (renderer == null) {
                // No renderer attached yet (e.g. before a world is loaded); not an error.
                return null;
            }
            return field.get(renderer);
        } catch (Throwable t) {
            instanceNullable = null;
            renderSectionManagerField = null;
            logFailure(
                    "SVE Embeddium compat: unable to reach the Embeddium render section manager; sparse sections will not render via Embeddium until it becomes reachable.",
                    t);
            return null;
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
            available = false;
            logFailure(
                    "SVE Embeddium compat: RenderSectionManager API is incompatible; Embeddium sparse-section rendering disabled for this session.",
                    t);
        }
    }

    private static void invoke(Method method, Object target, Object arg, String name) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, arg);
        } catch (Throwable t) {
            logFailure("SVE Embeddium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, String name) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target);
        } catch (Throwable t) {
            logFailure("SVE Embeddium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, int a, int b, int c, String name) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, a, b, c);
        } catch (Throwable t) {
            logFailure("SVE Embeddium compat: " + name + " failed", t);
        }
    }

    private static void invoke(Method method, Object target, int a, int b, int c, boolean d, String name) {
        if (method == null) {
            return;
        }
        try {
            method.invoke(target, a, b, c, d);
        } catch (Throwable t) {
            logFailure("SVE Embeddium compat: " + name + " failed", t);
        }
    }

    private static void logFailure(String message, Throwable cause) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn(message, cause);
        }
    }
}
