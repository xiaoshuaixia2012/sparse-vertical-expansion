package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExtendedSectionPayload(int chunkX, int chunkZ, int sectionY, int rulesMask, int[] stateIds) implements CustomPacketPayload {
    public static final int BLOCK_COUNT = 4096;
    public static final Type<ExtendedSectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "extended_section"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedSectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtendedSectionPayload::write, ExtendedSectionPayload::decode);

    public ExtendedSectionPayload {
        int minSection = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MIN_Y);
        int maxSection = SectionPos.blockToSectionCoord(ExtendedYRange.STANDARD_MAX_Y);
        if (sectionY < minSection || sectionY > maxSection || sectionY >= -4 && sectionY <= 19) {
            throw new IllegalArgumentException("payload sectionY is not in the standard extended range");
        }
        if (stateIds.length != BLOCK_COUNT) {
            throw new IllegalArgumentException("section payload must contain exactly 4096 block states");
        }
        SimulationRules.fromMask(rulesMask);
        stateIds = stateIds.clone();
    }

    @Override
    public int[] stateIds() {
        return stateIds.clone();
    }

    @Override
    public Type<ExtendedSectionPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(sectionY);
        buffer.writeByte(rulesMask);
        for (int stateId : stateIds) {
            buffer.writeVarInt(stateId);
        }
    }

    private static ExtendedSectionPayload decode(RegistryFriendlyByteBuf buffer) {
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        int sectionY = buffer.readInt();
        int rulesMask = buffer.readUnsignedByte();
        int[] states = new int[BLOCK_COUNT];
        for (int index = 0; index < states.length; index++) {
            states[index] = buffer.readVarInt();
        }
        return new ExtendedSectionPayload(chunkX, chunkZ, sectionY, rulesMask, states);
    }
}
