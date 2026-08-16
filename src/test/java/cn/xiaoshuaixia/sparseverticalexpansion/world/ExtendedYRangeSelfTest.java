package cn.xiaoshuaixia.sparseverticalexpansion.world;

public final class ExtendedYRangeSelfTest {
    public static void main(String[] args) {
        assertEquals(new ExtendedYRange(100_000, 100_015), ExtendedYRange.aligned(100_005, 100_005));
        assertEquals(new ExtendedYRange(2_000_000, 2_000_015), ExtendedYRange.aligned(2_000_000, 2_000_000));
        assertEquals(new ExtendedYRange(-80, -65), ExtendedYRange.aligned(-65, -65));
        assertEquals(new ExtendedYRange(320, 335), ExtendedYRange.aligned(320, 320));
        assertEquals(new ExtendedYRange(8_388_592, 8_388_607),
                ExtendedYRange.aligned(8_388_607, 8_388_607));
        assertEquals(new ExtendedYRange(-8_388_608, -8_388_593),
                ExtendedYRange.aligned(-8_388_608, -8_388_608));

        assertRejected(() -> ExtendedYRange.aligned(9, 8));
        assertRejected(() -> ExtendedYRange.aligned(-64, -64));
        assertRejected(() -> ExtendedYRange.aligned(319, 320));
        assertRejected(() -> ExtendedYRange.aligned(8_388_608, 8_388_608));
        assertRejected(() -> ExtendedYRange.aligned(-8_388_609, -8_388_609));
    }

    private static void assertEquals(ExtendedYRange expected, ExtendedYRange actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual);
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
