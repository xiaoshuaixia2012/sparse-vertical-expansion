package cn.xiaoshuaixia.sparseverticalexpansion.server;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SimulationRules;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalLayer;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VerticalRegion;
import cn.xiaoshuaixia.sparseverticalexpansion.world.VoidDamageMode;
import cn.xiaoshuaixia.sparseverticalexpansion.network.SveNetwork;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SveCommands {
    private SveCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sve")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("platform")
                        .requires(source -> SvePermissions.has(source, SvePermissions.REGION_EDIT))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> createPlatform(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "y"),
                                                        IntegerArgumentType.getInteger(context, "z")))))))
                .then(Commands.literal("region")
                        .then(Commands.literal("create")
                                .requires(source -> SvePermissions.has(source, SvePermissions.REGION_EDIT))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("chunkMinX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkMaxX", IntegerArgumentType.integer())
                                                        .then(Commands.argument("chunkMinZ", IntegerArgumentType.integer())
                                                                .then(Commands.argument("chunkMaxZ", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("minY", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("maxY", IntegerArgumentType.integer())
                                                                                        .executes(context -> createRegion(
                                                                                                context.getSource(),
                                                                                                StringArgumentType.getString(context, "name"),
                                                                                                IntegerArgumentType.getInteger(context, "chunkMinX"),
                                                                                                IntegerArgumentType.getInteger(context, "chunkMaxX"),
                                                                                                IntegerArgumentType.getInteger(context, "chunkMinZ"),
                                                                                                IntegerArgumentType.getInteger(context, "chunkMaxZ"),
                                                                                                IntegerArgumentType.getInteger(context, "minY"),
                                                                                                IntegerArgumentType.getInteger(context, "maxY"))))))))))
                        .then(Commands.literal("list").executes(context -> listRegions(context.getSource()))))
                .then(Commands.literal("config")
                        .requires(source -> SvePermissions.has(source, SvePermissions.CONFIG_EDIT))
                        .then(Commands.literal("list").executes(context -> listConfig(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.literal("default_extended_max_y")
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .executes(context -> setMaximumY(
                                                        context.getSource(), IntegerArgumentType.getInteger(context, "y")))))
                                .then(Commands.literal("disable_void_damage")
                                        .then(Commands.argument("mode", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("false", "player", "entity"), builder))
                                                .executes(context -> setVoidDamage(
                                                        context.getSource(), StringArgumentType.getString(context, "mode")))))))
                .then(Commands.literal("permission")
                        .requires(source -> SvePermissions.has(source, SvePermissions.COMMAND_ALL))
                        .then(Commands.literal("list").executes(context -> listPermissions(context.getSource())))
                        .then(Commands.literal("set")
                                .then(permissionArgument()
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 4))
                                                .executes(context -> setPermission(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "permission"),
                                                        IntegerArgumentType.getInteger(context, "level"))))))
                        .then(Commands.literal("reset")
                                .then(permissionArgument().executes(context -> resetPermission(
                                        context.getSource(), StringArgumentType.getString(context, "permission"))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> permissionArgument() {
        return Commands.argument("permission", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        SveWorldData.get(context.getSource().getLevel()).permissionLevels().keySet(), builder));
    }

    private static int help(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player
                && SvePermissions.has(source, SvePermissions.REGION_EDIT)) {
            SveNetwork.openRegionEditor(player, player.blockPosition());
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("commands.sve.help"), false);
        return 1;
    }

    private static int createRegion(
            CommandSourceStack source,
            String name,
            int chunkMinX,
            int chunkMaxX,
            int chunkMinZ,
            int chunkMaxZ,
            int minY,
            int maxY) {
        try {
            SveWorldData data = SveWorldData.get(source.getLevel());
            ExtendedYRange range = ExtendedYRange.aligned(minY, maxY);
            if (range.maxY() > data.defaultExtendedMaxY()) {
                throw new IllegalArgumentException("maximum Y exceeds the saved-world limit");
            }
            data.addRegion(new VerticalRegion(
                    name,
                    source.getLevel().dimension().location(),
                    chunkMinX,
                    chunkMaxX,
                    chunkMinZ,
                    chunkMaxZ,
                    List.of(new VerticalLayer(range, SimulationRules.DEFAULT))));
            source.sendSuccess(
                    () -> Component.translatable("commands.sve.region.created", name, range.minY(), range.maxY()), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int createPlatform(CommandSourceStack source, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (source.getLevel().getBlockState(pos).isAir()
                && source.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState())) {
            source.sendSuccess(() -> Component.translatable("commands.sve.platform.created", x, y, z), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.sve.platform.failed"));
        return 0;
    }

    private static int listRegions(CommandSourceStack source) {
        List<VerticalRegion> regions = SveWorldData.get(source.getLevel()).regions();
        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.sve.region.none"), false);
            return 1;
        }
        for (VerticalRegion region : regions) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.sve.region.entry",
                            region.name(),
                            region.dimension(),
                            region.chunkMinX(),
                            region.chunkMaxX(),
                            region.chunkMinZ(),
                            region.chunkMaxZ()),
                    false);
        }
        return regions.size();
    }

    private static int listConfig(CommandSourceStack source) {
        SveWorldData data = SveWorldData.get(source.getLevel());
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.sve.config.values",
                        data.defaultExtendedMaxY(),
                        data.disableVoidDamage().serializedName()),
                false);
        return 1;
    }

    private static int setMaximumY(CommandSourceStack source, int maxY) {
        return change(source, () -> SveWorldData.get(source.getLevel()).setDefaultExtendedMaxY(maxY));
    }

    private static int setVoidDamage(CommandSourceStack source, String value) {
        VoidDamageMode mode;
        try {
            mode = VoidDamageMode.parse(value);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        int result = change(source, () -> SveWorldData.get(source.getLevel()).setDisableVoidDamage(mode));
        if (result == 1 && mode == VoidDamageMode.ENTITY) {
            source.sendSystemMessage(Component.translatable("commands.sve.config.void_entity_warning"));
        }
        return result;
    }

    private static int listPermissions(CommandSourceStack source) {
        SveWorldData.get(source.getLevel()).permissionLevels().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> source.sendSuccess(
                        () -> Component.literal(entry.getKey() + " = " + entry.getValue()), false));
        return 1;
    }

    private static int setPermission(CommandSourceStack source, String permission, int level) {
        return change(source, () -> SveWorldData.get(source.getLevel()).setPermissionLevel(permission, level));
    }

    private static int resetPermission(CommandSourceStack source, String permission) {
        return change(source, () -> SveWorldData.get(source.getLevel()).resetPermissionLevel(permission));
    }

    private static int change(CommandSourceStack source, Runnable change) {
        try {
            change.run();
            source.sendSuccess(() -> Component.translatable("commands.sve.updated"), true);
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }
}
