package cn.xiaoshuaixia.sparseverticalexpansion.network;

import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A full extended section snapshot sent to the client: block states plus the optional separated sky/block light layers
 * (2048-byte {@link net.minecraft.world.level.chunk.DataLayer} nibble arrays). Light is {@code null} until the server
 * has relit the section, in which case the renderer falls back to its unlit default.
 */
public record ExtendedSectionPayload(
        int chunkX,
        int chunkZ,
        int sectionY,
        int rulesMask,
        int[] stateIds,
        @Nullable byte[] skyLight,
        @Nullable byte[] blockLight) implements CustomPacketPayload {
    public static final int BLOCK_COUNT = 4096;
    public static final int LIGHT_BYTES = 2048;
    public static final Type<ExtendedSectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SparseVerticalExpansion.MOD_ID, "extended_section"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedSectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(ExtendedSectionPayload::write, ExtendedSectionPayload::decode);

    public ExtendedSectionPayload(int chunkX, int chunkZ, int sectionY, int rulesMask, int[] stateIds) {
        this(chunkX, chunkZ, sectionY, rulesMask, stateIds, null, null);
    }

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
        validateLight(skyLight);
        validateLight(blockLight);
        stateIds = stateIds.clone();
        skyLight = skyLight == null ? null : skyLight.clone();
        blockLight = blockLight == null ? null : blockLight.clone();
    }

    private static void validateLight(@Nullable byte[] light) {
        if (light != null && light.length != LIGHT_BYTES) {
            throw new IllegalArgumentException("light layer must be exactly " + LIGHT_BYTES + " bytes");
        }
    }

    @Override
    public int[] stateIds() {
        return stateIds.clone();
    }

    @Nullable
    @Override
    public byte[] skyLight() {
        return skyLight == null ? null : skyLight.clone();
    }

    @Nullable
    @Override
    public byte[] blockLight() {
        return blockLight == null ? null : blockLight.clone();
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
        writeNullableLight(buffer, skyLight);
        writeNullableLight(buffer, blockLight);
    }

    private static void writeNullableLight(RegistryFriendlyByteBuf buffer, @Nullable byte[] light) {
        buffer.writeBoolean(light != null);
        if (light != null) {
            buffer.writeBytes(light);
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
        byte[] sky = readNullableLight(buffer);
        byte[] block = readNullableLight(buffer);
        return new ExtendedSectionPayload(chunkX, chunkZ, sectionY, rulesMask, states, sky, block);
    }

    @Nullable
    private static byte[] readNullableLight(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        byte[] light = new byte[LIGHT_BYTES];
        buffer.readBytes(light);
        return light;
    }
}
