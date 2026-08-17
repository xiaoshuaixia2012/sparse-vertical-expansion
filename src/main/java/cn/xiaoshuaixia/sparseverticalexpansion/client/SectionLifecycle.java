package cn.xiaoshuaixia.sparseverticalexpansion.client;

/**
 * Pure, side-effect-free lifecycle transition model for a sparse section's presence in the
 * renderer. Kept separate from {@link SodiumCompat} so the transition table can be tested without
 * Sodium, OpenGL, or a running game.
 */
public enum SectionLifecycle {
    /** No renderer notification needed. */
    NOOP,
    /** The section did not exist before and exists now: register it. */
    ADD,
    /** The section existed before and still exists: rebuild its mesh. */
    REBUILD,
    /** The section existed before but is gone now: unregister it. */
    REMOVE;

    /**
     * @param renderable whether the section's simulation rules permit rendering
     * @param wasNonAir  whether a non-air sparse section existed before the update
     * @param isNonAir   whether a non-air sparse section exists after the update
     */
    public static SectionLifecycle decide(boolean renderable, boolean wasNonAir, boolean isNonAir) {
        if (!renderable) {
            return wasNonAir ? REMOVE : NOOP;
        }
        if (isNonAir) {
            return wasNonAir ? REBUILD : ADD;
        }
        return wasNonAir ? REMOVE : NOOP;
    }
}
