package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

public record ExtendedUseItemOnPayload(
        int x,
        int y,
        int z,
        Direction face,
        InteractionHand hand,
        float hitX,
        float hitY,
        float hitZ,
        boolean inside) implements CustomPacketPayload {
    public static final Type<ExtendedUseItemOnPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "extended_use_item_on"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedUseItemOnPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtendedUseItemOnPayload::write, ExtendedUseItemOnPayload::decode);

    public ExtendedUseItemOnPayload {
        if (face == null || hand == null || !validHit(hitX) || !validHit(hitY) || !validHit(hitZ)) {
            throw new IllegalArgumentException("invalid extended block hit");
        }
        if (y < ExtendedYRange.STANDARD_MIN_Y
                || y > ExtendedYRange.STANDARD_MAX_Y
                || !isExtended(y) && !isExtended((long) y + face.getStepY())) {
            throw new IllegalArgumentException("block hit does not touch the standard extended range");
        }
    }

    @Override
    public Type<ExtendedUseItemOnPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeEnum(face);
        buffer.writeEnum(hand);
        buffer.writeFloat(hitX);
        buffer.writeFloat(hitY);
        buffer.writeFloat(hitZ);
        buffer.writeBoolean(inside);
    }

    private static ExtendedUseItemOnPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ExtendedUseItemOnPayload(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readEnum(Direction.class),
                buffer.readEnum(InteractionHand.class),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean());
    }

    private static boolean validHit(float value) {
        return Float.isFinite(value) && value >= -1.0F && value <= 2.0F;
    }

    private static boolean isExtended(long y) {
        return y >= ExtendedYRange.STANDARD_MIN_Y
                && y <= ExtendedYRange.STANDARD_MAX_Y
                && (y < ExtendedYRange.VANILLA_MIN_Y || y > ExtendedYRange.VANILLA_MAX_Y);
    }
}
