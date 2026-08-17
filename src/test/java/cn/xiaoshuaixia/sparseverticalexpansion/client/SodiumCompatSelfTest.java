package cn.xiaoshuaixia.sparseverticalexpansion.client;

public final class SodiumCompatSelfTest {
    public static void main(String[] args) {
        // Four-state presence transition for a renderable section.
        expect(SectionLifecycle.ADD, SectionLifecycle.decide(true, false, true), "absent->present registers");
        expect(SectionLifecycle.REBUILD, SectionLifecycle.decide(true, true, true), "present->present rebuilds");
        expect(SectionLifecycle.REMOVE, SectionLifecycle.decide(true, true, false), "present->absent unregisters");
        expect(SectionLifecycle.NOOP, SectionLifecycle.decide(true, false, false), "absent->absent is a no-op");

        // A non-renderable section is never registered; an existing one must still be removed.
        expect(SectionLifecycle.NOOP, SectionLifecycle.decide(false, false, true), "non-renderable never registers");
        expect(SectionLifecycle.NOOP, SectionLifecycle.decide(false, false, false), "non-renderable absent is a no-op");
        expect(SectionLifecycle.REMOVE, SectionLifecycle.decide(false, true, true), "non-renderable existing is removed");
        expect(SectionLifecycle.REMOVE, SectionLifecycle.decide(false, true, false), "non-renderable disappearance removes");

        // Vanilla section-Y boundaries (vanilla build range -64..319 => sections -4..19).
        expectTrue(SodiumSectionLookup.isVanillaSectionY(-4), "-64 must map to a vanilla section");
        expectTrue(SodiumSectionLookup.isVanillaSectionY(19), "319 must map to a vanilla section");
        expectTrue(!SodiumSectionLookup.isVanillaSectionY(-5), "-80 is outside vanilla");
        expectTrue(!SodiumSectionLookup.isVanillaSectionY(20), "320 is outside vanilla");
        expectTrue(!SodiumSectionLookup.isVanillaSectionY(6250), "100000 is an extended section");

        System.out.println("SodiumCompatSelfTest OK");
    }

    private static void expect(SectionLifecycle expected, SectionLifecycle actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void expectTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
