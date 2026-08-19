package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sable;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates the optional Sable (physics engine) compatibility mixins on the presence of Sable only.
 * Aeronautics and Offroad build on Sable and sample blocks through {@code LevelAccelerator}; the
 * mixin patches that one chokepoint, so any Sable version that keeps the same {@code LevelAccelerator}
 * signature is compatible. {@code defaultRequire: 1} makes an API change fail loudly at startup.
 */
public final class SableCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/sable-compat");
    private static final String SABLE_MOD_ID = "sable";

    private static boolean sablePresent;

    @Override
    public void onLoad(String mixinPackage) {
        sablePresent = isSablePresent();
        if (!sablePresent) {
            LOGGER.info("Sable is not installed; sparse-section Sable physics compat is disabled.");
        } else {
            LOGGER.info("Sable {} detected; sparse-section Sable physics compat enabled.", sableVersionOr("unknown"));
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sablePresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isSablePresent() {
        // Mixin configs load before mod containers are constructed, so query the discovered mod-file
        // list first and fall back to ModList for later stages.
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null && loading.getModFileById(SABLE_MOD_ID) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get() != null && ModList.get().isLoaded(SABLE_MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sableVersionOr(String fallback) {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null) {
                for (ModInfo mod : loading.getMods()) {
                    if (SABLE_MOD_ID.equals(mod.getModId())) {
                        return mod.getVersion().toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get().getModContainerById(SABLE_MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
