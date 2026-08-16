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
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

public final class SparseSectionStorage {
    private static final String SECTIONS_TAG = "sections";
    private static final String SECTION_Y_TAG = "section_y";
    private static final String BLOCK_STATES_TAG = "block_states";
    private static final int MIN_SECTION_Y = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MIN_Y);
    private static final int MAX_SECTION_Y = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MAX_Y);
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY,
            BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());

    public static final IAttachmentSerializer<CompoundTag, SparseSectionStorage> SERIALIZER = new Serializer();

    private final Int2ObjectMap<LevelChunkSection> sections = new Int2ObjectOpenHashMap<>();

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
            sections.remove(sectionY);
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
            sections.remove(sectionY);
        } else {
            sections.put(sectionY, section);
        }
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
                }
            }
            return storage;
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
                serializedSections.add(serializedSection);
            }

            CompoundTag tag = new CompoundTag();
            tag.put(SECTIONS_TAG, serializedSections);
            return tag;
        }
    }
}
