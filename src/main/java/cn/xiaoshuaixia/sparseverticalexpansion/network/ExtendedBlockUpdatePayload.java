package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record ExtendedBlockUpdatePayload(int x, int y, int z, BlockState state, int rulesMask) implements CustomPacketPayload {
    public static final Type<ExtendedBlockUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "extended_block_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedBlockUpdatePayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtendedBlockUpdatePayload::write, ExtendedBlockUpdatePayload::decode);

    public ExtendedBlockUpdatePayload {
        if (y < ExtendedYRange.STANDARD_MIN_Y
                || y > ExtendedYRange.STANDARD_MAX_Y
                || y >= ExtendedYRange.VANILLA_MIN_Y && y <= ExtendedYRange.VANILLA_MAX_Y) {
            throw new IllegalArgumentException("payload Y is not in the standard extended range");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        SimulationRules.fromMask(rulesMask);
    }

    @Override
    public Type<ExtendedBlockUpdatePayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeVarInt(Block.getId(state));
        buffer.writeByte(rulesMask);
    }

    private static ExtendedBlockUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ExtendedBlockUpdatePayload(
                buffer.readInt(), buffer.readInt(), buffer.readInt(), Block.stateById(buffer.readVarInt()), buffer.readUnsignedByte());
    }
}
