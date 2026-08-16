package cn.xiaoshuaixia.sparseverticalexpansion.world;

public enum VoidDamageMode {
    OFF("false"),
    PLAYER("player"),
    ENTITY("entity");

    private final String serializedName;

    VoidDamageMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean blocks(boolean player) {
        return this == ENTITY || this == PLAYER && player;
    }

    public static VoidDamageMode parse(String value) {
        for (VoidDamageMode mode : values()) {
            if (mode.serializedName.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown void damage mode: " + value);
    }
}
