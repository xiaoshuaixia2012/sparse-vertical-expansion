package cn.xiaoshuaixia.sparseverticalexpansion.mixin.compat.embeddium;

import cn.xiaoshuaixia.sparseverticalexpansion.client.EmbeddiumCompat;
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
 * Gates the optional Embeddium compatibility mixins on the <em>presence</em> of Embeddium only.
 * Version matching is handled by the {@code incompatible} dependency declarations in
 * neoforge.mods.toml and by the {@code defaultRequire: 1} mixin config (which fails loudly if an
 * Embeddium build changes its internal API).
 */
public final class EmbeddiumCompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("sparse_vertical_expansion/embeddium-compat");
    private static final String EMBEDDIUM_MOD_ID = "embeddium";

    private static boolean embeddiumPresent;

    @Override
    public void onLoad(String mixinPackage) {
        embeddiumPresent = isEmbeddiumPresent();
        if (!embeddiumPresent) {
            LOGGER.info("Embeddium is not installed; sparse-section Embeddium compat is disabled.");
        } else {
            LOGGER.info("Embeddium {} detected; sparse-section Embeddium compat enabled.",
                    embeddiumVersionOr("unknown"));
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return embeddiumPresent;
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

    private static boolean isEmbeddiumPresent() {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null && loading.getModFileById(EMBEDDIUM_MOD_ID) != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get() != null && ModList.get().isLoaded(EMBEDDIUM_MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String embeddiumVersionOr(String fallback) {
        try {
            LoadingModList loading = LoadingModList.get();
            if (loading != null) {
                for (ModInfo mod : loading.getMods()) {
                    if (EMBEDDIUM_MOD_ID.equals(mod.getModId())) {
                        return mod.getVersion().toString();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            return ModList.get().getModContainerById(EMBEDDIUM_MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
