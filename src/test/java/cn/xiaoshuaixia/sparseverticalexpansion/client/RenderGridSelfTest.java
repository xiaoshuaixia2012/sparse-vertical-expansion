package cn.xiaoshuaixia.sparseverticalexpansion.client;

import java.lang.reflect.Modifier;
import net.minecraft.core.BlockPos;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;

public final class RenderGridSelfTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        int changed = 0;
        for (int slot = 0; slot < 24; slot++) {
            int before = SveRenderer.wrappedOriginY(slot, 320, 24);
            int after = SveRenderer.wrappedOriginY(slot, 336, 24);
            if (before != after) changed++;
        }
        if (changed != 1) {
            throw new AssertionError("crossing one section must recycle exactly one render slot, got " + changed);
        }
        if (SveRenderer.slotForSection(43, 24) != 19
                || SveRenderer.slotForSection(44, 24) != 20) {
            throw new AssertionError("render slots must use absolute section-Y wrapping");
        }
        int modifiers = Class.forName("cn.xiaoshuaixia.sparseverticalexpansion.mixin.client.ViewAreaMixin")
                .getDeclaredMethod("getRenderSectionAt", BlockPos.class)
                .getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            throw new AssertionError("ViewArea#getRenderSectionAt overwrite must remain public");
        }
        if (SimulationRules.fromPersistedMask(15).mask() != SimulationRules.KNOWN_MASK) {
            throw new AssertionError("unknown saved simulation rule bits must be ignored for compatibility");
        }
    }
}
