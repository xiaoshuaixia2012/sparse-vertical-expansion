package cn.xiaoshuaixia.sparseverticalexpansion.world;

public record SimulationRules(int mask) {
    public static final int RENDERING = 1;
    public static final int COLLISION = 1 << 1;
    public static final int ENTITY_INTERACTION = 1 << 2;
    public static final int LIGHTING = 1 << 3;
    public static final int KNOWN_MASK = RENDERING | COLLISION | ENTITY_INTERACTION | LIGHTING;
    /** Default-on rules. Lighting is intentionally off by default because it involves per-chunk computation. */
    public static final int DEFAULT_MASK = RENDERING | COLLISION | ENTITY_INTERACTION;
    public static final SimulationRules DEFAULT = new SimulationRules(DEFAULT_MASK);

    public SimulationRules {
        if ((mask & ~KNOWN_MASK) != 0) {
            throw new IllegalArgumentException("unknown simulation rule bits");
        }
    }

    public static SimulationRules fromMask(int mask) {
        return new SimulationRules(mask);
    }

    public static SimulationRules fromPersistedMask(int mask) {
        if (mask < 0) {
            throw new IllegalArgumentException("simulation rule mask must not be negative");
        }
        return new SimulationRules(mask & KNOWN_MASK);
    }

    public boolean rendering() {
        return (mask & RENDERING) != 0;
    }

    public boolean collision() {
        return (mask & COLLISION) != 0;
    }

    public boolean entityInteraction() {
        return (mask & ENTITY_INTERACTION) != 0;
    }

    public boolean lighting() {
        return (mask & LIGHTING) != 0;
    }

}
