package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteRegionLayerPayload(int chunkX, int chunkZ, int minY, int maxY, int revision)
        implements CustomPacketPayload {
    public static final Type<DeleteRegionLayerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "delete_region_layer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteRegionLayerPayload> STREAM_CODEC =
            StreamCodec.ofMember(DeleteRegionLayerPayload::write, DeleteRegionLayerPayload::decode);

    public DeleteRegionLayerPayload {
        new ExtendedYRange(minY, maxY);
        if (revision < 0) {
            throw new IllegalArgumentException("invalid world-data revision");
        }
    }

    @Override
    public Type<DeleteRegionLayerPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        buffer.writeInt(revision);
    }

    private static DeleteRegionLayerPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DeleteRegionLayerPayload(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
