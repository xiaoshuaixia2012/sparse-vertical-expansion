package cn.xiaoshuaixia.sparseverticalexpansion.world;

import java.util.Objects;

public record VerticalLayer(ExtendedYRange range, SimulationRules rules) {
    public VerticalLayer {
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(rules, "rules");
    }
}
