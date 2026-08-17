package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.sodium;

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
 * Gates the optional Sodium compatibility mixins on the <em>presence</em> of Sodium only.
 *
 * <p>Version matching is deliberately not done here: the optional mixin config uses
 * {@code defaultRequire: 1}, so if a Sodium build ships an incompatible internal API the mixin
 * application fails loudly at startup instead of silently skipping sparse-section rendering. The
 * hard version boundaries are additionally declared as {@code incompatible} dependencies in
 * neoforge.mods.toml for a clear, early error message.</p>
 */
public final class SodiumCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/sodium-compat");
    private static final String SODIUM_MOD_ID = "sodium";

    private static boolean sodiumPresent;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumPresent = isSodiumPresent();
        if (!sodiumPresent) {
            LOGGER.info("Sodium is not installed; sparse-section Sodium compat is disabled.");
        } else {
            LOGGER.info("Sodium {} detected; sparse-section Sodium compat enabled.",
                    sodiumVersionOr("unknown"));
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return sodiumPresent;
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

    private static boolean isSodiumPresent() {
        // Mixin configs load before mod containers are constructed, so query the discovered mod-file
        // list first and fall back to ModList for later stages.
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null && loading.getModFileById(SODIUM_MOD_ID) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get() != null && ModList.get().isLoaded(SODIUM_MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String sodiumVersionOr(String fallback) {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null) {
                for (ModInfo mod : loading.getMods()) {
                    if (SODIUM_MOD_ID.equals(mod.getModId())) {
                        return mod.getVersion().toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get().getModContainerById(SODIUM_MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
