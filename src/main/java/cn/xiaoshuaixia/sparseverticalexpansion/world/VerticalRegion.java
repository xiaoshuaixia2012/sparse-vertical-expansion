package cn.xiaoshuaixia.sparseverticalexpansion.world;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record VerticalRegion(
        String name,
        ResourceLocation dimension,
        int chunkMinX,
        int chunkMaxX,
        int chunkMinZ,
        int chunkMaxZ,
        List<VerticalLayer> layers) {
    public static final int MAX_NAME_LENGTH = 64;

    public VerticalRegion {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("region name must contain 1 to 64 characters");
        }
        Objects.requireNonNull(dimension, "dimension");
        if (chunkMinX > chunkMaxX || chunkMinZ > chunkMaxZ) {
            throw new IllegalArgumentException("region Chunk bounds are reversed");
        }

        layers = Objects.requireNonNull(layers, "layers").stream()
                .map(layer -> Objects.requireNonNull(layer, "layer"))
                .sorted(Comparator.comparingInt(layer -> layer.range().minY()))
                .toList();
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("region must contain at least one vertical layer");
        }
        for (int index = 1; index < layers.size(); index++) {
            if (layers.get(index).range().minY() <= layers.get(index - 1).range().maxY()) {
                throw new IllegalArgumentException("vertical layers must not overlap");
            }
        }
    }

    public boolean contains(int chunkX, int chunkZ, int y) {
        if (chunkX < chunkMinX || chunkX > chunkMaxX || chunkZ < chunkMinZ || chunkZ > chunkMaxZ) {
            return false;
        }
        return layers.stream().anyMatch(layer -> y >= layer.range().minY() && y <= layer.range().maxY());
    }

    public boolean containsChunk(int chunkX, int chunkZ) {
        return chunkX >= chunkMinX && chunkX <= chunkMaxX && chunkZ >= chunkMinZ && chunkZ <= chunkMaxZ;
    }

    public Optional<VerticalLayer> findLayer(int y) {
        return layers.stream().filter(layer -> y >= layer.range().minY() && y <= layer.range().maxY()).findFirst();
    }

    public boolean overlapsFootprint(VerticalRegion other) {
        Objects.requireNonNull(other, "other");
        return dimension.equals(other.dimension)
                && chunkMinX <= other.chunkMaxX
                && chunkMaxX >= other.chunkMinX
                && chunkMinZ <= other.chunkMaxZ
                && chunkMaxZ >= other.chunkMinZ;
    }
}
