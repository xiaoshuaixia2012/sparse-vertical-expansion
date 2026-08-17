package cn.xiaoshuaixia.sparseverticalexpansion.client;

import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Pure-vanilla (Sodium-free) helper shared by the optional Sodium compatibility mixins.
 *
 * <p>It answers the two questions the compat layer needs at every height-sensitive read site:
 * is this section Y inside the immutable vanilla range, and does this chunk have a non-air
 * sparse {@link LevelChunkSection} at that Y. Keeping it here means the optional mixins never
 * have to reach into SVE storage internals or duplicate the vanilla-range arithmetic.</p>
 */
public final class SodiumSectionLookup {
    private static final int VANILLA_MIN_SECTION = Math.floorDiv(ExtendedYRange.VANILLA_MIN_Y, 16);
    private static final int VANILLA_MAX_SECTION_EXCLUSIVE = Math.floorDiv(ExtendedYRange.VANILLA_MAX_Y, 16) + 1;

    private SodiumSectionLookup() {
    }

    public static boolean isVanillaSectionY(int sectionY) {
        return sectionY >= VANILLA_MIN_SECTION && sectionY < VANILLA_MAX_SECTION_EXCLUSIVE;
    }

    /**
     * Returns the sparse section stored in the chunk's extended-section attachment at the given
     * absolute section Y, or {@code null} when the chunk has no attachment or no such section.
     */
    @Nullable
    public static LevelChunkSection sparseSection(LevelChunk chunk, int sectionY) {
        SparseSectionStorage storage = chunk.getExistingDataOrNull(SveAttachments.EXTENDED_SECTIONS.get());
        return storage == null ? null : storage.getSection(sectionY);
    }

    /** True when the requested sparse section exists and is not all air. */
    public static boolean hasSparseSection(LevelChunk chunk, int sectionY) {
        LevelChunkSection section = sparseSection(chunk, sectionY);
        return section != null && !section.hasOnlyAir();
    }
}
