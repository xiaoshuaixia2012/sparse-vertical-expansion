package cn.xiaoshuaixia.sparseverticalexpansion.compat;

import java.lang.reflect.Method;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-side reflection bridge that feeds SVE sparse block changes into Sable's Rapier physics engine.
 *
 * <p>Registration of sparse sections is handled lazily by {@code SablePhysicsChunkTicketManagerMixin},
 * which hooks Sable's own chunk-ticket manager so a sparse section is only voxelized when a ship's
 * bounding box approaches it (mirroring vanilla). The only thing this bridge does on the block-change
 * path is mirror Sable's cheap {@code SubLevelPhysicsSystem#handleBlockChange} (a 7-block incremental
 * update); it performs no full-section voxelization and no section registration, so placing or breaking
 * a sparse block stays as cheap as it is in vanilla chunks.</p>
 */
public final class SablePhysicsCompat {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/sable-physics");
    private static final String SABLE_MOD_ID = "sable";
    private static final String CONTAINER_CLASS = "dev.ryanhcode.sable.api.sublevel.SubLevelContainer";
    private static final String SERVER_CONTAINER_CLASS = "dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer";
    private static final String PHYSICS_SYSTEM_CLASS = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem";

    private static volatile Boolean available;
    private static volatile boolean failureLogged;
    /** Shared all-air section used only to satisfy {@code handleBlockChange}'s non-null section parameter. */
    private static volatile LevelChunkSection emptySection;

    private static Method getContainer;
    private static Method physicsSystemMethod;
    private static Method handleBlockChange;

    private SablePhysicsCompat() {
    }

    public static boolean isAvailable() {
        Boolean result = available;
        if (result == null) {
            synchronized (SablePhysicsCompat.class) {
                result = available;
                if (result == null) {
                    try {
                        result = ModList.get() != null && ModList.get().isLoaded(SABLE_MOD_ID);
                    } catch (Throwable ignored) {
                        result = false;
                    }
                    available = result;
                }
            }
        }
        return result;
    }

    private static void resolve() {
        if (getContainer != null) {
            return;
        }
        try {
            getContainer = Class.forName(CONTAINER_CLASS).getMethod("getContainer", ServerLevel.class);
            physicsSystemMethod = Class.forName(SERVER_CONTAINER_CLASS).getMethod("physicsSystem");
            Class<?> physicsSystem = Class.forName(PHYSICS_SYSTEM_CLASS);
            handleBlockChange = physicsSystem.getMethod(
                    "handleBlockChange",
                    SectionPos.class, LevelChunkSection.class, int.class, int.class, int.class, BlockState.class, BlockState.class);
        } catch (Throwable t) {
            getContainer = null;
            logFailure("SVE Sable physics compat: unable to resolve the Sable physics API; sparse-section physics collision disabled.", t);
        }
    }

    /**
     * Mirrors Sable's vanilla block-change path for a sparse block: a cheap, incremental 7-block update.
     * No full-section voxelization and no registration happen here.
     */
    public static void notifyBlockChange(
            ServerLevel level, int chunkX, int sectionY, int chunkZ, LevelChunkSection section,
            int localX, int localY, int localZ, BlockState oldState, BlockState newState) {
        if (!isAvailable()) {
            return;
        }
        resolve();
        if (getContainer == null) {
            return;
        }
        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) {
                return;
            }
            Object physicsSystem = physicsSystemMethod.invoke(container);
            if (section == null) {
                section = emptySection(level);
            }
            SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
            handleBlockChange.invoke(physicsSystem, sectionPos, section, localX, localY, localZ, oldState, newState);
        } catch (Throwable t) {
            logFailure("SVE Sable physics compat: failed to notify Sable of a block change.", t);
        }
    }

    private static LevelChunkSection emptySection(ServerLevel level) {
        LevelChunkSection section = emptySection;
        if (section == null) {
            section = new LevelChunkSection(level.registryAccess().registryOrThrow(Registries.BIOME));
            emptySection = section;
        }
        return section;
    }

    private static void logFailure(String message, Throwable cause) {
        if (!failureLogged) {
            failureLogged = true;
            LOGGER.warn(message, cause);
        }
    }
}
