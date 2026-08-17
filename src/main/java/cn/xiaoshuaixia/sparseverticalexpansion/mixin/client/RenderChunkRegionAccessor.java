package cn.xiaoshuaixia.sparseverticalexpansion.mixin.client;

import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the underlying {@link Level} of a vanilla chunk-compilation region so SVE can read sparse-section light from
 * the chunk attachment while the vanilla block-model renderer asks for light on a worker thread.
 */
@Mixin(RenderChunkRegion.class)
public interface RenderChunkRegionAccessor {
    @Accessor("level")
    Level sve$getLevel();
}
