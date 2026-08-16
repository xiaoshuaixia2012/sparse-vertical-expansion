package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExtendedBreakPayload(int x, int y, int z) implements CustomPacketPayload {
    public static final Type<ExtendedBreakPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "extended_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedBreakPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtendedBreakPayload::write, ExtendedBreakPayload::decode);

    public ExtendedBreakPayload {
        if (y < ExtendedYRange.STANDARD_MIN_Y
                || y > ExtendedYRange.STANDARD_MAX_Y
                || y >= ExtendedYRange.VANILLA_MIN_Y && y <= ExtendedYRange.VANILLA_MAX_Y) {
            throw new IllegalArgumentException("payload Y is not in the standard extended range");
        }
    }

    @Override
    public Type<ExtendedBreakPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
    }

    private static ExtendedBreakPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ExtendedBreakPayload(buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
