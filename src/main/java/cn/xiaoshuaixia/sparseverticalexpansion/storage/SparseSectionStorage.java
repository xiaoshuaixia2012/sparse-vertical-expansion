package cn.xiaoshuaixia.sparseverticalexpansion.storage;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

/**
 * Sparse per-section storage for extended-height blocks. In addition to the block states, every section can carry a
 * separated sky-light and block-light {@link DataLayer} (the 1.20+ split-light representation). Light is optional and
 * computed lazily by {@code SparseLightEngine}; a section with no light data simply falls back to the renderer's
 * unlit default.
 */
public final class SparseSectionStorage {
    /**
     * Version of the {@code SparseLightEngine} algorithm that produced the stored light. Old saves carry light computed
     * by an earlier algorithm (missing tag = 0); on load {@link #isLightStale(int)} flags such sections so the light
     * manager re-relights them once instead of keeping the frozen old result.
     */
    public static final int CURRENT_LIGHT_VERSION = 1;
    private static final String SECTIONS_TAG = "sections";
    private static final String SECTION_Y_TAG = "section_y";
    private static final String BLOCK_STATES_TAG = "block_states";
    private static final String SKY_LIGHT_TAG = "sky_light";
    private static final String BLOCK_LIGHT_TAG = "block_light";
    private static final String LIGHT_VERSION_TAG = "light_version";
    private static final int MIN_SECTION_Y = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MIN_Y);
    private static final int MAX_SECTION_Y = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MAX_Y);
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY,
            BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());

    public static final IAttachmentSerializer<CompoundTag, SparseSectionStorage> SERIALIZER = new Serializer();

    private final Int2ObjectMap<LevelChunkSection> sections = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<DataLayer> skyLight = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<DataLayer> blockLight = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Integer> lightVersions = new Int2ObjectOpenHashMap<>();

    public BlockState getBlockState(int x, int y, int z) {
        LevelChunkSection section = sections.get(SectionPos.blockToSectionCoord(y));
        return section == null ? Blocks.AIR.defaultBlockState() : section.getBlockState(x & 15, y & 15, z & 15);
    }

    @Nullable
    public BlockState setBlockState(int x, int y, int z, BlockState state, Registry<Biome> biomes) {
        int sectionY = SectionPos.blockToSectionCoord(y);
        LevelChunkSection section = sections.get(sectionY);
        if (section == null) {
            if (state.isAir()) {
                return null;
            }
            section = new LevelChunkSection(biomes);
            sections.put(sectionY, section);
        }

        BlockState previous = section.setBlockState(x & 15, y & 15, z & 15, state);
        if (section.hasOnlyAir()) {
            removeSection(sectionY);
        }
        return previous == state ? null : previous;
    }

    public boolean isSectionEmpty(int sectionY) {
        LevelChunkSection section = sections.get(sectionY);
        return section == null || section.hasOnlyAir();
    }

    public int sectionCount() {
        return sections.size();
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public int[] sectionYs() {
        int[] sectionYs = sections.keySet().toIntArray();
        Arrays.sort(sectionYs);
        return sectionYs;
    }

    @Nullable
    public LevelChunkSection getSection(int sectionY) {
        return sections.get(sectionY);
    }

    /** Returns the stored sky-light nibble layer for a section, or {@code null} when it has not been computed yet. */
    @Nullable
    public DataLayer getSkyLight(int sectionY) {
        return skyLight.get(sectionY);
    }

    /** Returns the stored block-light nibble layer for a section, or {@code null} when it has not been computed yet. */
    @Nullable
    public DataLayer getBlockLight(int sectionY) {
        return blockLight.get(sectionY);
    }

    public boolean hasLight(int sectionY) {
        return skyLight.containsKey(sectionY) || blockLight.containsKey(sectionY);
    }

    /**
     * True when a section has light but it was produced by an older light algorithm (or has no version tag at all),
     * so it should be re-relighted on load rather than trusted.
     */
    public boolean isLightStale(int sectionY) {
        if (!hasLight(sectionY)) {
            return false;
        }
        return lightVersions.getOrDefault(sectionY, Integer.valueOf(0)) < CURRENT_LIGHT_VERSION;
    }

    /** Replaces the light layers of an existing section. No-ops when the section does not exist. */
    public void setLight(int sectionY, @Nullable DataLayer sky, @Nullable DataLayer block) {
        if (!sections.containsKey(sectionY)) {
            return;
        }
        if (sky == null) {
            skyLight.remove(sectionY);
        } else {
            skyLight.put(sectionY, sky);
        }
        if (block == null) {
            blockLight.remove(sectionY);
        } else {
            blockLight.put(sectionY, block);
        }
        lightVersions.put(sectionY, Integer.valueOf(CURRENT_LIGHT_VERSION));
    }

    public void clearLight(int sectionY) {
        skyLight.remove(sectionY);
        blockLight.remove(sectionY);
        lightVersions.remove(sectionY);
    }

    /** Materializes the sky-light layer as a 2048-byte array, or {@code null} when absent. */
    @Nullable
    public byte[] skyLightBytes(int sectionY) {
        DataLayer layer = skyLight.get(sectionY);
        return layer == null ? null : layer.getData().clone();
    }

    /** Materializes the block-light layer as a 2048-byte array, or {@code null} when absent. */
    @Nullable
    public byte[] blockLightBytes(int sectionY) {
        DataLayer layer = blockLight.get(sectionY);
        return layer == null ? null : layer.getData().clone();
    }

    public int[] copyStateIds(int sectionY) {
        LevelChunkSection section = sections.get(sectionY);
        if (section == null) {
            throw new IllegalArgumentException("missing sparse section: " + sectionY);
        }
        int[] states = new int[4096];
        int index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    states[index++] = Block.getId(section.getBlockState(x, y, z));
                }
            }
        }
        return states;
    }

    public Optional<net.minecraft.core.BlockPos> firstNonAir(
            int minY, int maxY, int chunkMinBlockX, int chunkMinBlockZ) {
        for (int sectionY : sectionYs()) {
            int sectionMinY = sectionY << 4;
            if (sectionMinY > maxY) {
                break;
            }
            if (sectionMinY + 15 < minY) {
                continue;
            }
            LevelChunkSection section = sections.get(sectionY);
            for (int y = Math.max(minY, sectionMinY); y <= Math.min(maxY, sectionMinY + 15); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!section.getBlockState(x, y & 15, z).isAir()) {
                            return Optional.of(new net.minecraft.core.BlockPos(
                                    chunkMinBlockX + x, y, chunkMinBlockZ + z));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public void replaceSection(int sectionY, int[] stateIds, Registry<Biome> biomes) {
        replaceSection(sectionY, stateIds, null, null, biomes);
    }

    public void replaceSection(int sectionY, int[] stateIds, @Nullable byte[] sky, @Nullable byte[] block, Registry<Biome> biomes) {
        if (stateIds.length != 4096 || sectionY < MIN_SECTION_Y || sectionY > MAX_SECTION_Y) {
            throw new IllegalArgumentException("invalid sparse section snapshot");
        }
        LevelChunkSection section = new LevelChunkSection(biomes);
        int index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    section.setBlockState(x, y, z, Block.stateById(stateIds[index++]));
                }
            }
        }
        if (section.hasOnlyAir()) {
            removeSection(sectionY);
        } else {
            sections.put(sectionY, section);
            // The server always sends light computed by the current algorithm; the version only lags when re-reading
            // an old save, which goes through the serializer (readLightLayers below) instead of this network path.
            setLightLayers(sectionY, sky, block, CURRENT_LIGHT_VERSION);
        }
    }

    private void setLightLayers(int sectionY, @Nullable byte[] sky, @Nullable byte[] block, int version) {
        if (sky != null) {
            skyLight.put(sectionY, new DataLayer(sky));
        } else {
            skyLight.remove(sectionY);
        }
        if (block != null) {
            blockLight.put(sectionY, new DataLayer(block));
        } else {
            blockLight.remove(sectionY);
        }
        lightVersions.put(sectionY, Integer.valueOf(version));
    }

    private void removeSection(int sectionY) {
        sections.remove(sectionY);
        skyLight.remove(sectionY);
        blockLight.remove(sectionY);
        lightVersions.remove(sectionY);
    }

    private static final class Serializer implements IAttachmentSerializer<CompoundTag, SparseSectionStorage> {
        @Override
        public SparseSectionStorage read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            if (!(provider instanceof RegistryAccess registryAccess)) {
                throw new IllegalStateException("Chunk attachment deserialization requires RegistryAccess");
            }

            Registry<Biome> biomes = registryAccess.registryOrThrow(Registries.BIOME);
            SparseSectionStorage storage = new SparseSectionStorage();
            if (!tag.contains(SECTIONS_TAG, Tag.TAG_LIST)) {
                throw new IllegalArgumentException("missing sections list");
            }
            ListTag serializedSections = tag.getList(SECTIONS_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < serializedSections.size(); i++) {
                CompoundTag serializedSection = serializedSections.getCompound(i);
                if (!serializedSection.contains(SECTION_Y_TAG, Tag.TAG_INT)
                        || !serializedSection.contains(BLOCK_STATES_TAG, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("malformed sparse section at index " + i);
                }
                int sectionY = serializedSection.getInt(SECTION_Y_TAG);
                if (sectionY < MIN_SECTION_Y || sectionY > MAX_SECTION_Y) {
                    throw new IllegalArgumentException("sectionY outside standard-mode limits: " + sectionY);
                }
                if (storage.sections.containsKey(sectionY)) {
                    throw new IllegalArgumentException("duplicate sectionY: " + sectionY);
                }

                PalettedContainer<BlockState> states = BLOCK_STATE_CODEC
                        .parse(NbtOps.INSTANCE, serializedSection.getCompound(BLOCK_STATES_TAG))
                        .getOrThrow();
                LevelChunkSection section = new LevelChunkSection(
                        states,
                        new PalettedContainer<>(
                                biomes.asHolderIdMap(),
                                biomes.getHolderOrThrow(Biomes.PLAINS),
                                PalettedContainer.Strategy.SECTION_BIOMES));
                if (!section.hasOnlyAir()) {
                    storage.sections.put(sectionY, section);
                    int version = serializedSection.getInt(LIGHT_VERSION_TAG); // 0 when absent → stale
                    storage.setLightLayers(
                            sectionY,
                            readLightBytes(serializedSection, SKY_LIGHT_TAG),
                            readLightBytes(serializedSection, BLOCK_LIGHT_TAG),
                            version);
                }
            }
            return storage;
        }

        @Nullable
        private static byte[] readLightBytes(CompoundTag section, String key) {
            if (!section.contains(key, Tag.TAG_BYTE_ARRAY)) {
                return null;
            }
            byte[] bytes = section.getByteArray(key);
            if (bytes.length != DataLayer.SIZE) {
                throw new IllegalArgumentException(key + " must be " + DataLayer.SIZE + " bytes, got " + bytes.length);
            }
            return bytes;
        }

        @Nullable
        @Override
        public CompoundTag write(SparseSectionStorage storage, HolderLookup.Provider provider) {
            if (storage.isEmpty()) {
                return null;
            }

            ListTag serializedSections = new ListTag();
            for (int sectionY : storage.sectionYs()) {
                CompoundTag serializedSection = new CompoundTag();
                serializedSection.putInt(SECTION_Y_TAG, sectionY);
                serializedSection.put(
                        BLOCK_STATES_TAG,
                        BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, storage.sections.get(sectionY).getStates()).getOrThrow());
                DataLayer sky = storage.skyLight.get(sectionY);
                if (sky != null) {
                    serializedSection.putByteArray(SKY_LIGHT_TAG, sky.getData());
                }
                DataLayer block = storage.blockLight.get(sectionY);
                if (block != null) {
                    serializedSection.putByteArray(BLOCK_LIGHT_TAG, block.getData());
                }
                serializedSection.putInt(LIGHT_VERSION_TAG, storage.lightVersions.getOrDefault(sectionY, Integer.valueOf(0)));
                serializedSections.add(serializedSection);
            }

            CompoundTag tag = new CompoundTag();
            tag.put(SECTIONS_TAG, serializedSections);
            return tag;
        }
    }
}
