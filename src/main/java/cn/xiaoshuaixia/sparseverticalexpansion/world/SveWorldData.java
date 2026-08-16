package cn.xiaoshuaixia.sparseverticalexpansion.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SveWorldData extends SavedData {
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_ID = "sparse_vertical_expansion";
    private static final int DEFAULT_EXTENDED_MAX_Y = 2_000_015;
    private static final Map<String, Integer> DEFAULT_PERMISSION_LEVELS = Map.of(
            "sve.extended.build", 0,
            "sve.region.edit", 2,
            "sve.config.edit", 3,
            "sve.experimental.edit", 4,
            "sve.command.all", 4);
    private static final Factory<SveWorldData> FACTORY = new Factory<>(SveWorldData::new, SveWorldData::load);

    private final Map<String, VerticalRegion> regionsByName = new LinkedHashMap<>();
    private final Map<String, Integer> permissionLevels = new LinkedHashMap<>(DEFAULT_PERMISSION_LEVELS);
    private final CompoundTag preservedTag;
    private int revision;
    private int defaultExtendedMaxY = DEFAULT_EXTENDED_MAX_Y;
    private VoidDamageMode disableVoidDamage = VoidDamageMode.OFF;

    public SveWorldData() {
        this.preservedTag = null;
    }

    private SveWorldData(CompoundTag preservedTag) {
        this.preservedTag = preservedTag;
    }

    public static SveWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static SveWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag original = tag.copy();
        try {
            if (requiredInt(tag, "schema_version") != SCHEMA_VERSION) {
                return new SveWorldData(original);
            }

            SveWorldData data = new SveWorldData();
            data.revision = requiredInt(tag, "revision");
            if (data.revision < 0) {
                throw new IllegalArgumentException("revision must not be negative");
            }
            ListTag regions = requiredList(tag, "regions");
            for (int index = 0; index < regions.size(); index++) {
                data.putLoaded(decodeRegion(regions.getCompound(index)));
            }
            if (tag.contains("default_extended_max_y", Tag.TAG_INT)) {
                data.defaultExtendedMaxY = alignDefaultMaximum(tag.getInt("default_extended_max_y"));
            }
            if (tag.contains("disable_void_damage", Tag.TAG_STRING)) {
                data.disableVoidDamage = VoidDamageMode.parse(tag.getString("disable_void_damage"));
            }
            if (tag.contains("permission_levels", Tag.TAG_COMPOUND)) {
                CompoundTag levels = tag.getCompound("permission_levels");
                for (String permission : levels.getAllKeys()) {
                    if (!DEFAULT_PERMISSION_LEVELS.containsKey(permission) || !levels.contains(permission, Tag.TAG_INT)) {
                        throw new IllegalArgumentException("unknown or malformed permission level: " + permission);
                    }
                    data.permissionLevels.put(permission, validatePermissionLevel(levels.getInt(permission)));
                }
            }
            return data;
        } catch (RuntimeException ignored) {
            return new SveWorldData(original);
        }
    }

    public void addRegion(VerticalRegion region) {
        ensureWritable();
        validateAddition(region);
        regionsByName.put(region.name(), region);
        revision++;
        setDirty();
    }

    public Optional<VerticalRegion> findRegion(ResourceLocation dimension, int chunkX, int chunkZ, int y) {
        return regionsByName.values().stream()
                .filter(region -> region.dimension().equals(dimension) && region.contains(chunkX, chunkZ, y))
                .findFirst();
    }

    public Optional<VerticalRegion> findRegion(ResourceLocation dimension, int chunkX, int chunkZ) {
        return regionsByName.values().stream()
                .filter(region -> region.dimension().equals(dimension) && region.containsChunk(chunkX, chunkZ))
                .findFirst();
    }

    public VerticalRegion addLayer(ResourceLocation dimension, int chunkX, int chunkZ, VerticalLayer layer, int expectedRevision) {
        ensureWritable();
        if (revision != expectedRevision) {
            throw new IllegalStateException("region settings changed; reopen the editor");
        }
        Optional<VerticalRegion> existing = findRegion(dimension, chunkX, chunkZ);
        VerticalRegion updated;
        if (existing.isPresent()) {
            VerticalRegion region = existing.get();
            List<VerticalLayer> layers = new ArrayList<>(region.layers());
            layers.add(layer);
            updated = new VerticalRegion(
                    region.name(), region.dimension(), region.chunkMinX(), region.chunkMaxX(),
                    region.chunkMinZ(), region.chunkMaxZ(), layers);
            regionsByName.put(region.name(), updated);
            changed();
        } else {
            String base = "chunk_" + chunkX + "_" + chunkZ;
            String name = base;
            for (int suffix = 2; regionsByName.containsKey(name); suffix++) {
                name = base + "_" + suffix;
            }
            updated = new VerticalRegion(name, dimension, chunkX, chunkX, chunkZ, chunkZ, List.of(layer));
            addRegion(updated);
        }
        return updated;
    }

    public Optional<VerticalRegion> removeLayer(
            ResourceLocation dimension, int chunkX, int chunkZ, ExtendedYRange range, int expectedRevision) {
        ensureWritable();
        if (revision != expectedRevision) {
            throw new IllegalStateException("region settings changed; reopen the editor");
        }
        VerticalRegion region = findRegion(dimension, chunkX, chunkZ)
                .orElseThrow(() -> new IllegalArgumentException("this Chunk has no vertical region"));
        List<VerticalLayer> layers = new ArrayList<>(region.layers());
        if (!layers.removeIf(layer -> layer.range().equals(range))) {
            throw new IllegalArgumentException("no vertical layer exactly matches that Y range");
        }
        if (layers.isEmpty()) {
            regionsByName.remove(region.name());
            changed();
            return Optional.empty();
        }
        VerticalRegion updated = new VerticalRegion(
                region.name(), region.dimension(), region.chunkMinX(), region.chunkMaxX(),
                region.chunkMinZ(), region.chunkMaxZ(), layers);
        regionsByName.put(region.name(), updated);
        changed();
        return Optional.of(updated);
    }

    public List<VerticalRegion> regions() {
        return List.copyOf(regionsByName.values());
    }

    public int revision() {
        return revision;
    }

    public int defaultExtendedMaxY() {
        return defaultExtendedMaxY;
    }

    public void setDefaultExtendedMaxY(int maxY) {
        ensureWritable();
        int aligned = alignDefaultMaximum(maxY);
        if (defaultExtendedMaxY != aligned) {
            defaultExtendedMaxY = aligned;
            changed();
        }
    }

    public VoidDamageMode disableVoidDamage() {
        return disableVoidDamage;
    }

    public void setDisableVoidDamage(VoidDamageMode mode) {
        ensureWritable();
        mode = Objects.requireNonNull(mode, "mode");
        if (disableVoidDamage != mode) {
            disableVoidDamage = mode;
            changed();
        }
    }

    public int permissionLevel(String permission) {
        Integer level = permissionLevels.get(permission);
        if (level == null) {
            throw new IllegalArgumentException("unknown SVE permission: " + permission);
        }
        return level;
    }

    public Map<String, Integer> permissionLevels() {
        return Map.copyOf(permissionLevels);
    }

    public void setPermissionLevel(String permission, int level) {
        ensureWritable();
        if (!DEFAULT_PERMISSION_LEVELS.containsKey(permission)) {
            throw new IllegalArgumentException("unknown SVE permission: " + permission);
        }
        level = validatePermissionLevel(level);
        if (permissionLevels.put(permission, level) != level) {
            changed();
        }
    }

    public void resetPermissionLevel(String permission) {
        setPermissionLevel(permission, DEFAULT_PERMISSION_LEVELS.getOrDefault(permission, -1));
    }

    public boolean isReadOnly() {
        return preservedTag != null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (isReadOnly()) {
            return preservedTag.copy();
        }

        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.putInt("revision", revision);
        tag.putInt("default_extended_max_y", defaultExtendedMaxY);
        tag.putString("disable_void_damage", disableVoidDamage.serializedName());
        CompoundTag permissionLevelsTag = new CompoundTag();
        permissionLevels.forEach(permissionLevelsTag::putInt);
        tag.put("permission_levels", permissionLevelsTag);
        ListTag regions = new ListTag();
        regionsByName.values().stream().map(SveWorldData::encodeRegion).forEach(regions::add);
        tag.put("regions", regions);
        return tag;
    }

    private void putLoaded(VerticalRegion region) {
        validateAddition(region);
        regionsByName.put(region.name(), region);
    }

    private void validateAddition(VerticalRegion region) {
        Objects.requireNonNull(region, "region");
        if (regionsByName.containsKey(region.name())) {
            throw new IllegalArgumentException("region name already exists: " + region.name());
        }
        if (regionsByName.values().stream().anyMatch(region::overlapsFootprint)) {
            throw new IllegalArgumentException("region footprint overlaps an existing region");
        }
    }

    private void ensureWritable() {
        if (isReadOnly()) {
            throw new IllegalStateException("world data is read-only because its schema is unknown or malformed");
        }
    }

    private void changed() {
        revision++;
        setDirty();
    }

    private static int alignDefaultMaximum(int maxY) {
        return ExtendedYRange.aligned(ExtendedYRange.VANILLA_MAX_Y + 1, maxY).maxY();
    }

    private static int validatePermissionLevel(int level) {
        if (level < 0 || level > 4) {
            throw new IllegalArgumentException("permission level must be between 0 and 4");
        }
        return level;
    }

    private static CompoundTag encodeRegion(VerticalRegion region) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", region.name());
        tag.putString("dimension", region.dimension().toString());
        tag.putInt("chunk_min_x", region.chunkMinX());
        tag.putInt("chunk_max_x", region.chunkMaxX());
        tag.putInt("chunk_min_z", region.chunkMinZ());
        tag.putInt("chunk_max_z", region.chunkMaxZ());
        ListTag layers = new ListTag();
        for (VerticalLayer layer : region.layers()) {
            CompoundTag layerTag = new CompoundTag();
            layerTag.putInt("min_y", layer.range().minY());
            layerTag.putInt("max_y", layer.range().maxY());
            layerTag.putInt("rules", layer.rules().mask());
            layers.add(layerTag);
        }
        tag.put("layers", layers);
        return tag;
    }

    private static VerticalRegion decodeRegion(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(requiredString(tag, "dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("invalid dimension id");
        }

        ListTag layerTags = requiredList(tag, "layers");
        List<VerticalLayer> layers = new java.util.ArrayList<>(layerTags.size());
        for (int index = 0; index < layerTags.size(); index++) {
            CompoundTag layerTag = layerTags.getCompound(index);
            layers.add(new VerticalLayer(
                    new ExtendedYRange(requiredInt(layerTag, "min_y"), requiredInt(layerTag, "max_y")),
                    SimulationRules.fromPersistedMask(requiredInt(layerTag, "rules"))));
        }
        return new VerticalRegion(
                requiredString(tag, "name"),
                dimension,
                requiredInt(tag, "chunk_min_x"),
                requiredInt(tag, "chunk_max_x"),
                requiredInt(tag, "chunk_min_z"),
                requiredInt(tag, "chunk_max_z"),
                layers);
    }

    private static int requiredInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing integer: " + key);
        }
        return tag.getInt(key);
    }

    private static String requiredString(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("missing string: " + key);
        }
        return tag.getString(key);
    }

    private static ListTag requiredList(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        if (!(value instanceof ListTag list)) {
            throw new IllegalArgumentException("missing list: " + key);
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("list must contain compounds: " + key);
        }
        return list;
    }
}
