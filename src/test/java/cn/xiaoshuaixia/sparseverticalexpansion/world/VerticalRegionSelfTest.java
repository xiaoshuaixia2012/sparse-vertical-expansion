package cn.xiaoshuaixia.sparseverticalexpansion.world;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class VerticalRegionSelfTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");

    public static void main(String[] args) {
        SimulationRules rules = SimulationRules.fromMask(SimulationRules.DEFAULT.mask());
        assertEquals(SimulationRules.DEFAULT, rules);
        assertRejected(() -> SimulationRules.fromMask(1 << 8));

        VerticalLayer upper = new VerticalLayer(new ExtendedYRange(320, 335), rules);
        VerticalLayer next = new VerticalLayer(new ExtendedYRange(336, 351), rules);
        VerticalRegion region = region("space", OVERWORLD, 10, 12, 20, 22, List.of(next, upper));

        assertTrue(region.contains(10, 20, 320), "minimum boundaries must be included");
        assertTrue(region.contains(12, 22, 351), "maximum boundaries must be included");
        assertTrue(!region.contains(9, 20, 320), "outside Chunk X must not match");
        assertTrue(!region.contains(10, 20, 319), "locked vanilla Y must not match");

        assertRejected(() -> region("overlapping-layers", OVERWORLD, 0, 0, 0, 0, List.of(
                new VerticalLayer(new ExtendedYRange(320, 351), rules),
                new VerticalLayer(new ExtendedYRange(336, 367), rules))));

        VerticalRegion footprintOverlap = region("overlap", OVERWORLD, 12, 13, 22, 23, List.of(
                new VerticalLayer(new ExtendedYRange(100_000, 100_015), rules)));
        VerticalRegion differentDimension = region("nether", NETHER, 10, 12, 20, 22, List.of(upper));
        VerticalRegion adjacent = region("adjacent", OVERWORLD, 13, 14, 20, 22, List.of(upper));
        assertTrue(region.overlapsFootprint(footprintOverlap), "same-dimension footprints must overlap regardless of Y");
        assertTrue(!region.overlapsFootprint(differentDimension), "different dimensions must not overlap");
        assertTrue(!region.overlapsFootprint(adjacent), "edge-adjacent Chunk rectangles must not overlap");

        assertRejected(() -> region(" ", OVERWORLD, 0, 0, 0, 0, List.of(upper)));
        assertRejected(() -> region("x".repeat(65), OVERWORLD, 0, 0, 0, 0, List.of(upper)));
        assertRejected(() -> region("reversed-x", OVERWORLD, 1, 0, 0, 0, List.of(upper)));
        assertRejected(() -> region("reversed-z", OVERWORLD, 0, 0, 1, 0, List.of(upper)));

        assertTrue(!VoidDamageMode.OFF.blocks(true), "false must keep player void damage");
        assertTrue(VoidDamageMode.PLAYER.blocks(true), "player mode must protect players");
        assertTrue(!VoidDamageMode.PLAYER.blocks(false), "player mode must not protect other entities");
        assertTrue(VoidDamageMode.ENTITY.blocks(false), "entity mode must protect every entity");
    }

    private static VerticalRegion region(
            String name,
            ResourceLocation dimension,
            int chunkMinX,
            int chunkMaxX,
            int chunkMinZ,
            int chunkMaxZ,
            List<VerticalLayer> layers) {
        return new VerticalRegion(name, dimension, chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ, layers);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
