package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenRegionEditorPayload(
        int chunkX,
        int chunkZ,
        int suggestedMinY,
        int suggestedMaxY,
        int maximumY,
        int revision,
        List<VerticalLayer> layers)
        implements CustomPacketPayload {
    public static final Type<OpenRegionEditorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "open_region_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRegionEditorPayload> STREAM_CODEC =
            StreamCodec.ofMember(OpenRegionEditorPayload::write, OpenRegionEditorPayload::decode);

    public OpenRegionEditorPayload {
        new ExtendedYRange(suggestedMinY, suggestedMaxY);
        if (maximumY < ExtendedYRange.VANILLA_MAX_Y + 1 || maximumY > ExtendedYRange.STANDARD_MAX_Y || revision < 0) {
            throw new IllegalArgumentException("invalid region editor request");
        }
        layers = List.copyOf(layers);
        if (layers.size() > 4096) {
            throw new IllegalArgumentException("too many vertical layers for the editor");
        }
    }

    @Override
    public Type<OpenRegionEditorPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(suggestedMinY);
        buffer.writeInt(suggestedMaxY);
        buffer.writeInt(maximumY);
        buffer.writeInt(revision);
        buffer.writeVarInt(layers.size());
        for (VerticalLayer layer : layers) {
            buffer.writeInt(layer.range().minY());
            buffer.writeInt(layer.range().maxY());
            buffer.writeInt(layer.rules().mask());
        }
    }

    private static OpenRegionEditorPayload decode(RegistryFriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        int minY = buffer.readInt();
        int maxY = buffer.readInt();
        int maximumY = buffer.readInt();
        int revision = buffer.readInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > 4096) {
            throw new IllegalArgumentException("invalid vertical layer count");
        }
        List<VerticalLayer> layers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            layers.add(new VerticalLayer(
                    new ExtendedYRange(buffer.readInt(), buffer.readInt()),
                    SimulationRules.fromMask(buffer.readInt())));
        }
        return new OpenRegionEditorPayload(chunkX, chunkZ, minY, maxY, maximumY, revision, layers);
    }
}
