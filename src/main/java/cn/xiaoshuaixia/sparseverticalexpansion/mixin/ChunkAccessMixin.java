package cn.xiaoshuaixia.sparseverticalexpansion.mixin;

import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkAccess.class)
abstract class ChunkAccessMixin {
    @Inject(method = "isSectionEmpty", at = @At("HEAD"), cancellable = true)
    private void sve$isExtendedSectionEmpty(int sectionY, CallbackInfoReturnable<Boolean> callback) {
        ChunkAccess chunk = (ChunkAccess) (Object) this;
        if (sectionY >= chunk.getMinSection() && sectionY < chunk.getMaxSection()) {
            return;
        }

        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        callback.setReturnValue(storage == null || storage.isSectionEmpty(sectionY));
    }
}
