package cn.xiaoshuaixia.sparseverticalexpansion.world;

public record ExtendedYRange(int minY, int maxY) {
    public static final int SECTION_SIZE = 16;
    public static final int VANILLA_MIN_Y = -64;
    public static final int VANILLA_MAX_Y = 319;
    public static final int STANDARD_MIN_Y = -8_388_608;
    public static final int STANDARD_MAX_Y = 8_388_607;

    public ExtendedYRange {
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must be <= maxY");
        }
        if (minY < STANDARD_MIN_Y || maxY > STANDARD_MAX_Y) {
            throw new IllegalArgumentException("range exceeds standard-mode Y limits");
        }
        if (Math.floorMod(minY, SECTION_SIZE) != 0
                || Math.floorMod(maxY, SECTION_SIZE) != SECTION_SIZE - 1) {
            throw new IllegalArgumentException("range must align to complete sections");
        }
        if (minY <= VANILLA_MAX_Y && maxY >= VANILLA_MIN_Y) {
            throw new IllegalArgumentException("the vanilla build range is immutable");
        }
    }

    public static ExtendedYRange aligned(int minY, int maxY) {
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must be <= maxY");
        }
        if (minY < STANDARD_MIN_Y || maxY > STANDARD_MAX_Y) {
            throw new IllegalArgumentException("range exceeds standard-mode Y limits");
        }
        int alignedMinY = Math.floorDiv(minY, SECTION_SIZE) * SECTION_SIZE;
        int alignedMaxY = Math.floorDiv(maxY, SECTION_SIZE) * SECTION_SIZE + SECTION_SIZE - 1;
        return new ExtendedYRange(alignedMinY, alignedMaxY);
    }

}
