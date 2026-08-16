package cn.xiaoshuaixia.sparseverticalexpansion.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public final class MinecraftCoordinatePackingSelfTest {
    public static void main(String[] args) {
        assertBlockPosRoundTrip(2_047, 2_047);
        assertBlockPosRoundTrip(2_048, -2_048);
        assertBlockPosRoundTrip(100_000, 1_696);
        assertBlockPosRoundTrip(2_000_015, 1_167);
        assertBlockPosRoundTrip(8_388_607, -1);
        assertBlockPosRoundTrip(-8_388_608, 0);

        assertSectionPosRoundTrip(6_250);
        assertSectionPosRoundTrip(125_000);
        assertSectionPosRoundTrip(524_287);
        assertSectionPosRoundTrip(-524_288);
    }

    private static void assertBlockPosRoundTrip(int inputY, int expectedY) {
        int actualY = BlockPos.of(new BlockPos(0, inputY, 0).asLong()).getY();
        if (actualY != expectedY) {
            throw new AssertionError("BlockPos Y " + inputY + " decoded as " + actualY + ", expected " + expectedY);
        }
    }

    private static void assertSectionPosRoundTrip(int sectionY) {
        int actualY = SectionPos.of(SectionPos.asLong(0, sectionY, 0)).getY();
        if (actualY != sectionY) {
            throw new AssertionError("SectionPos Y " + sectionY + " decoded as " + actualY);
        }
    }
}
