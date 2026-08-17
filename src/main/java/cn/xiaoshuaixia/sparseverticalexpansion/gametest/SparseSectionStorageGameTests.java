package cn.xiaoshuaixia.sparseverticalexpansion.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import cn.xiaoshuaixia.sparseverticalexpansion.SparseVerticalExpansion;
import cn.xiaoshuaixia.sparseverticalexpansion.network.ExtendedBlockUpdatePayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.ExtendedSectionPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.CreateRegionLayerPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.DeleteRegionLayerPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.network.OpenRegionEditorPayload;
import cn.xiaoshuaixia.sparseverticalexpansion.lighting.SparseLightEngine;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveInteraction;
import cn.xiaoshuaixia.sparseverticalexpansion.registry.SveAttachments;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SvePermissions;
import cn.xiaoshuaixia.sparseverticalexpansion.server.SveCommandValidation;
import cn.xiaoshuaixia.sparseverticalexpansion.storage.SparseSectionStorage;
import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalRegion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VoidDamageMode;
import java.util.List;
import java.util.Arrays;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder(SparseVerticalExpansion.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SparseSectionStorageGameTests {
    private SparseSectionStorageGameTests() {
    }

    @GameTest(template = "empty")
    public static void allocatesAndReclaimsSections(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        SparseSectionStorage storage = new SparseSectionStorage();
        BlockPos pos = new BlockPos(1, 100000, 1);

        helper.assertTrue(storage.sectionCount() == 0, "storage must start empty");
        storage.setBlockState(pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE.defaultBlockState(), biomes);
        helper.assertTrue(storage.sectionCount() == 1, "first block must allocate one section");
        helper.assertTrue(storage.getBlockState(pos.getX(), pos.getY(), pos.getZ()).is(Blocks.STONE), "stone must read back");
        helper.assertTrue(
                storage.firstNonAir(100000, 100015, 0, 0).orElseThrow().equals(pos),
                "the sparse occupancy scan must return the first stored non-air coordinate");

        storage.setBlockState(pos.getX(), pos.getY(), pos.getZ(), Blocks.AIR.defaultBlockState(), biomes);
        helper.assertTrue(storage.sectionCount() == 0, "removing the last block must reclaim the section");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void notifiesVisibleExtendedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(3072, 0, 0);
        BlockPos pos = new BlockPos(base.getX(), 320, base.getZ());
        LevelChunk chunk = level.getChunkAt(pos);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_visible_notification_" + chunkX + "_" + chunkZ,
                level.dimension().location(),
                chunkX,
                chunkX,
                chunkZ,
                chunkZ,
                List.of(new VerticalLayer(ExtendedYRange.aligned(320, 320), SimulationRules.DEFAULT))));

        helper.assertTrue(
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()),
                "a visible extended block update must not index the vanilla section array");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalPlayerBreaksExtendedBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 0);
        BlockPos pos = new BlockPos(base.getX(), 320, base.getZ());
        LevelChunk chunk = level.getChunkAt(pos);
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_survival_break_" + chunk.getPos().x + "_" + chunk.getPos().z,
                level.dimension().location(),
                chunk.getPos().x,
                chunk.getPos().x,
                chunk.getPos().z,
                chunk.getPos().z,
                List.of(new VerticalLayer(ExtendedYRange.aligned(320, 320), SimulationRules.DEFAULT))));
        helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()), "test block must be placed");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5);
        helper.assertTrue(player.gameMode.destroyBlock(pos), "server player destroy must accept the extended block");
        helper.assertTrue(level.getBlockState(pos).isAir(), "server player destroy must remove the extended block");
        helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()), "test block must be restored");
        player.gameMode.handleBlockBreakAction(
                pos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP,
                ExtendedYRange.STANDARD_MAX_Y + 1, 1);
        for (int i = 0; i < 70; i++) {
            player.gameMode.tick();
        }
        player.gameMode.handleBlockBreakAction(
                pos, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP,
                ExtendedYRange.STANDARD_MAX_Y + 1, 2);
        helper.assertTrue(level.getBlockState(pos).isAir(), "server mining state machine must remove the extended block");
        helper.assertTrue(level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()), "test block must be restored again");
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, 3));
        for (int i = 0; i < 70; i++) {
            player.gameMode.tick();
        }
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, 4));

        helper.assertTrue(level.getBlockState(pos).isAir(), "one vanilla survival mining cycle must remove the extended block");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validatesExtendedVanillaBlockCommands(GameTestHelper helper) throws CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).offset(5120, 0, 0);
        LevelChunk chunk = level.getChunkAt(base);
        int boundaryX = (chunk.getPos().x << 4) + 15;
        BlockPos configured = new BlockPos(boundaryX, 320, base.getZ());
        BlockPos unconfigured = configured.east();
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_commands_" + chunk.getPos().x + "_" + chunk.getPos().z,
                level.dimension().location(),
                chunk.getPos().x,
                chunk.getPos().x,
                chunk.getPos().z,
                chunk.getPos().z,
                List.of(new VerticalLayer(ExtendedYRange.aligned(320, 320), SimulationRules.DEFAULT))));
        helper.assertTrue(SveCommandValidation.isConfigured(level, configured),
                "configured extended position must pass shared edit validation");
        helper.assertTrue(!SveCommandValidation.isConfigured(level, unconfigured),
                "unconfigured extended position must fail shared edit validation");
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPermission(4);

        int changed = level.getServer().getCommands().getDispatcher().execute(
                "setblock " + configured.getX() + " 320 " + configured.getZ() + " minecraft:stone", source);
        helper.assertTrue(changed == 1 && level.getBlockState(configured).is(Blocks.STONE),
                "/setblock must write inside a configured extended region");
        level.setBlock(configured, Blocks.AIR.defaultBlockState(), 3);

        try {
            level.getServer().getCommands().getDispatcher().execute(
                    "fill " + configured.getX() + " 320 " + configured.getZ() + " "
                            + unconfigured.getX() + " 320 " + unconfigured.getZ() + " minecraft:dirt",
                    source);
            throw new AssertionError("/fill must reject an unconfigured extended position");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(expected.getRawMessage().getString().contains(
                            unconfigured.getX() + " " + unconfigured.getY() + " " + unconfigured.getZ()),
                    "/fill rejection must report the first unconfigured coordinate");
        }
        helper.assertTrue(level.getBlockState(configured).isAir(), "rejected /fill must not modify earlier positions");

        level.setBlock(configured, Blocks.STONE.defaultBlockState(), 3);
        try {
            level.getServer().getCommands().getDispatcher().execute(
                    "clone " + configured.getX() + " 320 " + configured.getZ() + " "
                            + configured.getX() + " 320 " + configured.getZ() + " "
                            + unconfigured.getX() + " 320 " + unconfigured.getZ() + " replace move",
                    source);
            throw new AssertionError("/clone move must reject an unconfigured destination");
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(expected.getRawMessage().getString().contains(
                            unconfigured.getX() + " " + unconfigured.getY() + " " + unconfigured.getZ()),
                    "/clone rejection must report the first unconfigured destination, got: "
                            + expected.getRawMessage().getString());
        }
        helper.assertTrue(level.getBlockState(configured).is(Blocks.STONE),
                "rejected /clone move must not remove its source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void routesExtendedChunkAccess(GameTestHelper helper) {
        BlockPos rawOrigin = helper.absolutePos(BlockPos.ZERO).offset(1024, 0, 0);
        BlockPos origin = new BlockPos(rawOrigin.getX() & ~15, rawOrigin.getY(), rawOrigin.getZ() & ~15);
        LevelChunk chunk = helper.getLevel().getChunkAt(origin);
        BlockPos untouched = new BlockPos(origin.getX(), 100001, origin.getZ());
        BlockPos high = new BlockPos(origin.getX(), 100000, origin.getZ());
        BlockPos low = new BlockPos(origin.getX(), -100000, origin.getZ());

        helper.assertTrue(!chunk.hasData(SveAttachments.EXTENDED_SECTIONS.get()), "untouched chunk must not have SVE data");
        helper.assertTrue(chunk.getBlockState(untouched).isAir(), "untouched extended position must be air");
        helper.assertTrue(!chunk.hasData(SveAttachments.EXTENDED_SECTIONS.get()), "extended reads must not allocate SVE data");
        helper.assertTrue(
                chunk.setBlockState(high, Blocks.STONE.defaultBlockState(), false) == null,
                "an unconfigured Chunk must reject extended writes");
        helper.assertTrue(!chunk.hasData(SveAttachments.EXTENDED_SECTIONS.get()), "rejected writes must not allocate SVE data");

        openTestRegion(helper.getLevel(), chunk);

        BlockPos levelPath = new BlockPos(origin.getX() + 1, 100002, origin.getZ() + 1);
        helper.assertTrue(
                helper.getLevel().setBlockAndUpdate(levelPath, Blocks.STONE.defaultBlockState()),
                "Level.setBlock must route configured extended positions");
        helper.assertTrue(helper.getLevel().getBlockState(levelPath).is(Blocks.STONE), "Level.getBlockState must read sparse data");
        helper.assertTrue(
                !helper.getLevel().noCollision(
                        null,
                        new AABB(
                                levelPath.getX() + 0.2,
                                levelPath.getY() + 0.2,
                                levelPath.getZ() + 0.2,
                                levelPath.getX() + 0.8,
                                levelPath.getY() + 0.8,
                                levelPath.getZ() + 0.8)),
                "extended solid blocks must participate in collision queries");
        helper.getLevel().removeBlock(levelPath, false);

        chunk.setBlockState(high, Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(low, Blocks.DEEPSLATE.defaultBlockState(), false);
        helper.assertTrue(chunk.getBlockState(high).is(Blocks.STONE), "high extended state must read back");
        helper.assertTrue(chunk.getBlockState(low).is(Blocks.DEEPSLATE), "negative extended state must read back");
        helper.assertTrue(!chunk.isSectionEmpty(6250), "stored extended section must not be empty");

        Player mockPlayer = helper.makeMockPlayer(GameType.CREATIVE);
        mockPlayer.setPos(high.getX() + 0.5, high.getY() + 3.0, high.getZ() + 0.5);
        ItemStack blockItem = new ItemStack(Blocks.STONE);
        mockPlayer.setItemInHand(InteractionHand.MAIN_HAND, blockItem);
        BlockHitResult highTop = new BlockHitResult(Vec3.atCenterOf(high), Direction.UP, high, false);
        helper.assertTrue(
                SveInteraction.modifiedPos(mockPlayer, InteractionHand.MAIN_HAND, highTop).equals(high.above()),
                "dynamic expansion must validate the actual placement position above an existing layer");
        InteractionResult placement = blockItem.useOn(new UseOnContext(
                mockPlayer,
                InteractionHand.MAIN_HAND,
                highTop));
        helper.assertTrue(placement.consumesAction(), "a normal BlockItem must place through the extended Level path");
        helper.assertTrue(chunk.getBlockState(high.above()).is(Blocks.STONE), "placed extended block must read back");

        BlockPos blockEntity = new BlockPos(origin.getX(), 100016, origin.getZ());
        helper.assertTrue(
                chunk.setBlockState(blockEntity, Blocks.CHEST.defaultBlockState(), false) == null,
                "extended block entities must be rejected in phase 1");
        helper.assertTrue(chunk.getBlockState(blockEntity).isAir(), "rejected block entity position must stay air");

        BlockPos vanilla = new BlockPos(origin.getX(), 64, origin.getZ());
        chunk.setBlockState(vanilla, Blocks.STONE.defaultBlockState(), false);
        helper.assertTrue(chunk.getBlockState(vanilla).is(Blocks.STONE), "vanilla state must still use the original path");

        BlockPos invalid = new BlockPos(origin.getX(), ExtendedYRange.STANDARD_MAX_Y + 1, origin.getZ());
        helper.assertTrue(chunk.setBlockState(invalid, Blocks.STONE.defaultBlockState(), false) == null, "invalid Y write must be rejected");
        helper.assertTrue(chunk.getBlockState(invalid).is(Blocks.VOID_AIR), "invalid Y read must return void air");

        BlockPos noCollisionOrigin = origin.offset(64, 0, 0);
        LevelChunk noCollisionChunk = helper.getLevel().getChunkAt(noCollisionOrigin);
        BlockPos noCollisionBlock = new BlockPos(noCollisionOrigin.getX(), 200_000, noCollisionOrigin.getZ());
        SveWorldData.get(helper.getLevel()).addRegion(new VerticalRegion(
                "gametest_no_collision_" + noCollisionChunk.getPos().x + "_" + noCollisionChunk.getPos().z,
                helper.getLevel().dimension().location(),
                noCollisionChunk.getPos().x,
                noCollisionChunk.getPos().x,
                noCollisionChunk.getPos().z,
                noCollisionChunk.getPos().z,
                List.of(new VerticalLayer(
                        ExtendedYRange.aligned(200_000, 200_000),
                        SimulationRules.fromMask(SimulationRules.RENDERING | SimulationRules.ENTITY_INTERACTION)))));
        helper.assertTrue(
                helper.getLevel().setBlockAndUpdate(noCollisionBlock, Blocks.STONE.defaultBlockState()),
                "a no-collision layer must still store blocks");
        helper.assertTrue(
                helper.getLevel().noCollision(null, new AABB(noCollisionBlock)),
                "a layer with collision disabled must be ignored by collision queries");
        helper.getLevel().removeBlock(noCollisionBlock, false);

        chunk.setBlockState(high, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(high.above(), Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(low, Blocks.AIR.defaultBlockState(), false);
        helper.assertTrue(chunk.getData(SveAttachments.EXTENDED_SECTIONS.get()).sectionCount() == 0, "last blocks must reclaim both sections");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relightsSparseSections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rawOrigin = helper.absolutePos(BlockPos.ZERO).offset(8192, 0, 0);
        BlockPos origin = new BlockPos(rawOrigin.getX() & ~15, rawOrigin.getY(), rawOrigin.getZ() & ~15);
        LevelChunk chunk = level.getChunkAt(origin);
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_lighting_" + chunk.getPos().x + "_" + chunk.getPos().z,
                level.dimension().location(),
                chunk.getPos().x,
                chunk.getPos().x,
                chunk.getPos().z,
                chunk.getPos().z,
                List.of(new VerticalLayer(
                        ExtendedYRange.aligned(100000, 100000),
                        SimulationRules.fromMask(SimulationRules.DEFAULT.mask() | SimulationRules.LIGHTING)))));

        BlockPos base = new BlockPos(origin.getX(), 100000, origin.getZ());
        level.setBlockAndUpdate(base.offset(5, 0, 5), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(8, 1, 8), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(8, 2, 8), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(10, 0, 10), Blocks.STONE.defaultBlockState());

        helper.assertTrue(
                SparseLightEngine.relightChunk(chunk),
                "relightChunk must write light for the centre chunk");

        SparseSectionStorage storage = chunk.getData(SveAttachments.EXTENDED_SECTIONS.get());
        int sectionY = SectionPos.blockToSectionCoord(100000);
        DataLayer block = storage.getBlockLight(sectionY);
        DataLayer sky = storage.getSkyLight(sectionY);
        helper.assertTrue(block != null && sky != null, "separated sky and block light must both be computed");

        helper.assertTrue(block.get(5, 0, 5) == 15, "glowstone cell must hold its 15 emission");
        helper.assertTrue(block.get(6, 0, 5) == 14, "air one block from glowstone must be 14");
        helper.assertTrue(block.get(9, 0, 5) == 11, "air four blocks from glowstone must be 11");

        helper.assertTrue(sky.get(10, 0, 10) == 15, "an isolated block under open sky must be 15");
        helper.assertTrue(sky.get(8, 2, 8) == 15, "the top stone of a column must be 15");
        helper.assertTrue(sky.get(8, 1, 8) == 0, "the stone directly under another stone must be 0");

        for (BlockPos pos : new BlockPos[] {
                base.offset(5, 0, 5), base.offset(8, 1, 8), base.offset(8, 2, 8), base.offset(10, 0, 10)
        }) {
            level.removeBlock(pos, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void relightsAcrossSectionBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rawOrigin = helper.absolutePos(BlockPos.ZERO).offset(9216, 0, 0);
        BlockPos origin = new BlockPos(rawOrigin.getX() & ~15, rawOrigin.getY(), rawOrigin.getZ() & ~15);
        LevelChunk chunk = level.getChunkAt(origin);
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_lighting_across_" + chunk.getPos().x + "_" + chunk.getPos().z,
                level.dimension().location(),
                chunk.getPos().x,
                chunk.getPos().x,
                chunk.getPos().z,
                chunk.getPos().z,
                List.of(new VerticalLayer(
                        ExtendedYRange.aligned(100000, 100031),
                        SimulationRules.fromMask(SimulationRules.DEFAULT.mask() | SimulationRules.LIGHTING)))));

        BlockPos base = new BlockPos(origin.getX(), 100000, origin.getZ());
        // glowstone at section 6250 top (local 5,15,5); stone two blocks above in section 6251.
        level.setBlockAndUpdate(base.offset(5, 15, 5), Blocks.GLOWSTONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(5, 17, 5), Blocks.STONE.defaultBlockState());

        helper.assertTrue(SparseLightEngine.relightChunk(chunk), "relight must write light");

        SparseSectionStorage storage = chunk.getData(SveAttachments.EXTENDED_SECTIONS.get());
        DataLayer blockUpper = storage.getBlockLight(6251);
        helper.assertTrue(blockUpper != null, "section 6251 block light must be computed");
        helper.assertTrue(
                blockUpper.get(5, 0, 5) == 14,
                "air one block above the glowstone across the section boundary must be 14, got " + blockUpper.get(5, 0, 5));
        helper.assertTrue(
                blockUpper.get(5, 1, 5) == 0,
                "stone two blocks above the glowstone must be dark, got " + blockUpper.get(5, 1, 5));

        level.removeBlock(base.offset(5, 15, 5), false);
        level.removeBlock(base.offset(5, 17, 5), false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistsThroughChunkSerializer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(2048, 0, 0);
        LevelChunk chunk = level.getChunkAt(origin);
        BlockPos high = new BlockPos(origin.getX(), 100000, origin.getZ());
        BlockPos low = new BlockPos(origin.getX(), -100000, origin.getZ());

        openTestRegion(level, chunk);
        chunk.setBlockState(high, Blocks.STONE.defaultBlockState(), false);
        chunk.setBlockState(low, Blocks.DEEPSLATE.defaultBlockState(), false);
        CompoundTag serialized = ChunkSerializer.write(level, chunk);
        ProtoChunk decoded = ChunkSerializer.read(
                level,
                level.getChunkSource().getPoiManager(),
                new RegionStorageInfo("sve-gametest", level.dimension(), "chunk"),
                chunk.getPos(),
                serialized);

        helper.assertTrue(decoded.hasData(SveAttachments.EXTENDED_SECTIONS.get()), "chunk NBT must contain SVE attachment");
        SparseSectionStorage restored = decoded.getData(SveAttachments.EXTENDED_SECTIONS.get());
        helper.assertTrue(restored.sectionCount() == 2, "two sparse sections must survive chunk NBT round-trip");
        helper.assertTrue(restored.getBlockState(high.getX(), high.getY(), high.getZ()).is(Blocks.STONE), "high state must survive chunk NBT");
        helper.assertTrue(restored.getBlockState(low.getX(), low.getY(), low.getZ()).is(Blocks.DEEPSLATE), "low state must survive chunk NBT");
        helper.assertTrue(
                SparseSectionStorage.SERIALIZER.write(new SparseSectionStorage(), level.registryAccess()) == null,
                "empty storage must not serialize");

        BlockPos neighborPos = origin.offset(16, 0, 0);
        LevelChunk neighbor = level.getChunkAt(neighborPos);
        helper.assertTrue(!neighbor.hasData(SveAttachments.EXTENDED_SECTIONS.get()), "untouched neighbor must keep the no-attachment fast path");

        chunk.setBlockState(high, Blocks.AIR.defaultBlockState(), false);
        chunk.setBlockState(low, Blocks.AIR.defaultBlockState(), false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistsVerticalRegions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation overworld = ResourceLocation.parse("minecraft:overworld");
        VerticalLayer upper = new VerticalLayer(new ExtendedYRange(320, 335), SimulationRules.DEFAULT);
        VerticalRegion space = new VerticalRegion("space", overworld, 10, 12, 20, 22, List.of(upper));
        SveWorldData data = new SveWorldData();

        data.addRegion(space);
        helper.assertTrue(data.revision() == 1, "adding a region must increment the revision");
        helper.assertTrue(data.isDirty(), "adding a region must mark world data dirty");
        helper.assertTrue(data.findRegion(overworld, 12, 22, 335).orElseThrow().equals(space), "region must be findable");
        assertRejected(IllegalArgumentException.class, () -> data.addRegion(new VerticalRegion(
                "space", overworld, 30, 30, 30, 30, List.of(upper))));
        assertRejected(IllegalArgumentException.class, () -> data.addRegion(new VerticalRegion(
                "overlap",
                overworld,
                12,
                13,
                22,
                23,
                List.of(new VerticalLayer(new ExtendedYRange(100_000, 100_015), SimulationRules.DEFAULT)))));

        VerticalRegion nether = new VerticalRegion(
                "nether",
                ResourceLocation.parse("minecraft:the_nether"),
                10,
                12,
                20,
                22,
                List.of(upper));
        data.addRegion(nether);
        helper.assertTrue(data.revision() == 2, "another dimension may reuse the same footprint");

        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        SveWorldData restored = SveWorldData.load(saved, level.registryAccess());
        helper.assertTrue(restored.revision() == 2, "revision must survive world-data NBT");
        helper.assertTrue(restored.regions().equals(List.of(space, nether)), "regions and rules must survive world-data NBT");

        SveWorldData editorData = new SveWorldData();
        VerticalRegion edited = editorData.addLayer(
                overworld, 40, 50, new VerticalLayer(new ExtendedYRange(100_000, 100_015), SimulationRules.DEFAULT), 0);
        helper.assertTrue(edited.contains(40, 50, 100_000), "the editor must create a missing single-chunk region");
        edited = editorData.addLayer(
                overworld, 40, 50, new VerticalLayer(new ExtendedYRange(200_000, 200_015), SimulationRules.DEFAULT), 1);
        helper.assertTrue(edited.layers().size() == 2, "the editor must append a non-overlapping layer");
        assertRejected(
                IllegalArgumentException.class,
                () -> editorData.addLayer(
                        overworld, 40, 50,
                        new VerticalLayer(new ExtendedYRange(200_000, 200_015), SimulationRules.DEFAULT), 2));
        assertRejected(
                IllegalStateException.class,
                () -> editorData.addLayer(
                        overworld, 40, 50,
                        new VerticalLayer(new ExtendedYRange(300_000, 300_015), SimulationRules.DEFAULT), 1));
        VerticalRegion afterRemoval = editorData.removeLayer(
                overworld, 40, 50, new ExtendedYRange(100_000, 100_015), 2).orElseThrow();
        helper.assertTrue(
                afterRemoval.layers().equals(List.of(new VerticalLayer(
                        new ExtendedYRange(200_000, 200_015), SimulationRules.DEFAULT))),
                "deleting one layer must preserve the other layers");
        helper.assertTrue(editorData.revision() == 3, "deleting a layer must increment the revision");
        helper.assertTrue(
                editorData.removeLayer(overworld, 40, 50, new ExtendedYRange(200_000, 200_015), 3).isEmpty()
                        && editorData.findRegion(overworld, 40, 50).isEmpty(),
                "deleting the final layer must remove the empty region");

        CompoundTag unknown = new CompoundTag();
        unknown.putInt("schema_version", 99);
        unknown.putString("sentinel", "keep");
        SveWorldData readOnly = SveWorldData.load(unknown, level.registryAccess());
        helper.assertTrue(readOnly.isReadOnly(), "unknown schemas must load read-only");
        assertRejected(IllegalStateException.class, () -> readOnly.addRegion(space));
        helper.assertTrue(
                readOnly.save(new CompoundTag(), level.registryAccess()).equals(unknown),
                "read-only data must preserve the original NBT exactly");

        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schema_version", 1);
        helper.assertTrue(
                SveWorldData.load(malformed, level.registryAccess()).isReadOnly(),
                "malformed current-schema data must load read-only");

        SveWorldData config = new SveWorldData();
        helper.assertTrue(config.defaultExtendedMaxY() == 2_000_015, "default maximum Y must cover two million");
        helper.assertTrue(config.disableVoidDamage() == VoidDamageMode.OFF, "void damage must default to false");
        helper.assertTrue(config.permissionLevel("sve.extended.build") == 0, "all players must build by default");
        config.setDefaultExtendedMaxY(4_000_000);
        config.setDisableVoidDamage(VoidDamageMode.PLAYER);
        config.setPermissionLevel("sve.extended.build", 2);
        assertRejected(IllegalArgumentException.class, () -> config.setPermissionLevel("sve.extended.build", 5));
        SveWorldData restoredConfig = SveWorldData.load(
                config.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertTrue(restoredConfig.defaultExtendedMaxY() == 4_000_015, "maximum Y must align upward to a section");
        helper.assertTrue(restoredConfig.disableVoidDamage() == VoidDamageMode.PLAYER, "void damage mode must survive NBT");
        helper.assertTrue(restoredConfig.permissionLevel("sve.extended.build") == 2, "permission levels must survive NBT");
        helper.assertTrue(
                PermissionAPI.getRegisteredNodes().contains(SvePermissions.EXTENDED_BUILD),
                "SVE permission nodes must be registered");
        helper.assertTrue(
                level.getServer().getCommands().getDispatcher().getRoot().getChild("sve") != null,
                "/sve must be registered");

        RegistryFriendlyByteBuf updateBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        ExtendedBlockUpdatePayload update = new ExtendedBlockUpdatePayload(
                17, ExtendedYRange.STANDARD_MAX_Y, -23, Blocks.STONE.defaultBlockState(), SimulationRules.DEFAULT.mask());
        ExtendedBlockUpdatePayload.STREAM_CODEC.encode(updateBuffer, update);
        helper.assertTrue(
                ExtendedBlockUpdatePayload.STREAM_CODEC.decode(updateBuffer).equals(update),
                "block update payload must preserve separate int coordinates");

        int[] states = new int[4096];
        states[123] = Block.getId(Blocks.STONE.defaultBlockState());
        RegistryFriendlyByteBuf sectionBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        ExtendedSectionPayload section = new ExtendedSectionPayload(17, -23, 6250, SimulationRules.DEFAULT.mask(), states);
        ExtendedSectionPayload.STREAM_CODEC.encode(sectionBuffer, section);
        ExtendedSectionPayload restoredSection = ExtendedSectionPayload.STREAM_CODEC.decode(sectionBuffer);
        helper.assertTrue(
                restoredSection.chunkX() == 17
                        && restoredSection.chunkZ() == -23
                        && restoredSection.sectionY() == 6250
                        && Arrays.equals(restoredSection.stateIds(), states),
                "section payload must preserve one bounded 16-cubed section");
        assertRejected(IllegalArgumentException.class, () -> new ExtendedSectionPayload(0, 0, 6250, 0, new int[4095]));

        RegistryFriendlyByteBuf blockPosBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        BlockPos widePos = new BlockPos(17, ExtendedYRange.STANDARD_MAX_Y, -23);
        blockPosBuffer.writeBlockPos(widePos);
        helper.assertTrue(
                blockPosBuffer.readableBytes() == 12 && blockPosBuffer.readBlockPos().equals(widePos),
                "vanilla BlockPos packets must preserve wide Y using three ints");
        OpenRegionEditorPayload openEditor = new OpenRegionEditorPayload(
                3, -4, 100_000, 100_015, 2_000_015, 7,
                List.of(new VerticalLayer(new ExtendedYRange(100_000, 100_015), SimulationRules.DEFAULT)));
        RegistryFriendlyByteBuf openEditorBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        OpenRegionEditorPayload.STREAM_CODEC.encode(openEditorBuffer, openEditor);
        helper.assertTrue(
                OpenRegionEditorPayload.STREAM_CODEC.decode(openEditorBuffer).equals(openEditor),
                "editor request must preserve chunk, range, limit, revision, and existing layers");
        CreateRegionLayerPayload createLayer = new CreateRegionLayerPayload(
                3, -4, 100_001, 100_002, SimulationRules.RENDERING | SimulationRules.COLLISION, 7);
        RegistryFriendlyByteBuf createLayerBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        CreateRegionLayerPayload.STREAM_CODEC.encode(createLayerBuffer, createLayer);
        helper.assertTrue(
                CreateRegionLayerPayload.STREAM_CODEC.decode(createLayerBuffer).equals(createLayer),
                "editor submission must preserve raw Y inputs, rules, and revision");
        DeleteRegionLayerPayload deleteLayer = new DeleteRegionLayerPayload(3, -4, 100_000, 100_015, 8);
        RegistryFriendlyByteBuf deleteLayerBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        DeleteRegionLayerPayload.STREAM_CODEC.encode(deleteLayerBuffer, deleteLayer);
        helper.assertTrue(
                DeleteRegionLayerPayload.STREAM_CODEC.decode(deleteLayerBuffer).equals(deleteLayer),
                "editor deletion must preserve its exact aligned range and revision");
        helper.succeed();
    }

    private static void assertRejected(Class<? extends RuntimeException> expectedType, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (expectedType.isInstance(exception)) {
                return;
            }
            throw exception;
        }
        throw new AssertionError("expected " + expectedType.getSimpleName());
    }

    private static void openTestRegion(ServerLevel level, LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        SveWorldData.get(level).addRegion(new VerticalRegion(
                "gametest_" + chunkX + "_" + chunkZ,
                level.dimension().location(),
                chunkX,
                chunkX,
                chunkZ,
                chunkZ,
                List.of(
                        new VerticalLayer(ExtendedYRange.aligned(-100_000, -100_000), SimulationRules.DEFAULT),
                        new VerticalLayer(ExtendedYRange.aligned(100_000, 100_000), SimulationRules.DEFAULT))));
    }
}
