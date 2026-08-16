package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateRegionLayerPayload(
        int chunkX, int chunkZ, int minY, int maxY, int rulesMask, int revision) implements CustomPacketPayload {
    public static final Type<CreateRegionLayerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "create_region_layer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateRegionLayerPayload> STREAM_CODEC =
            StreamCodec.ofMember(CreateRegionLayerPayload::write, CreateRegionLayerPayload::decode);

    public CreateRegionLayerPayload {
        if (minY < ExtendedYRange.STANDARD_MIN_Y || maxY > ExtendedYRange.STANDARD_MAX_Y || minY > maxY) {
            throw new IllegalArgumentException("invalid vertical range");
        }
        SimulationRules.fromMask(rulesMask);
        if (revision < 0) {
            throw new IllegalArgumentException("invalid world-data revision");
        }
    }

    @Override
    public Type<CreateRegionLayerPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(minY);
        buffer.writeInt(maxY);
        buffer.writeInt(rulesMask);
        buffer.writeInt(revision);
    }

    private static CreateRegionLayerPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CreateRegionLayerPayload(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
