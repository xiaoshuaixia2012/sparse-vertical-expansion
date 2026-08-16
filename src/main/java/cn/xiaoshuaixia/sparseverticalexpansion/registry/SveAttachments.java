package cn.xiaoshuaixia.sparseverticalexpansion.registry;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SveAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SparseVerticalExpansion.MOD_ID);

    public static final Supplier<AttachmentType<SparseSectionStorage>> EXTENDED_SECTIONS = ATTACHMENTS.register(
            "extended_sections",
            () -> AttachmentType.builder(SparseSectionStorage::new)
                    .serialize(SparseSectionStorage.SERIALIZER)
                    .build());

    private SveAttachments() {
    }
}
