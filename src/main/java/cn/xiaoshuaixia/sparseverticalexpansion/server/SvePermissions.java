package cn.xiaoshuaixia.sparseverticalexpansion.server;

import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public final class SvePermissions {
    public static final PermissionNode<Boolean> EXTENDED_BUILD = node("extended.build");
    public static final PermissionNode<Boolean> REGION_EDIT = node("region.edit");
    public static final PermissionNode<Boolean> CONFIG_EDIT = node("config.edit");
    public static final PermissionNode<Boolean> EXPERIMENTAL_EDIT = node("experimental.edit");
    public static final PermissionNode<Boolean> COMMAND_ALL = node("command.all");

    private SvePermissions() {
    }

    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(EXTENDED_BUILD, REGION_EDIT, CONFIG_EDIT, EXPERIMENTAL_EDIT, COMMAND_ALL);
    }

    public static boolean has(CommandSourceStack source, PermissionNode<Boolean> permission) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }
        return PermissionAPI.getPermission(player, permission)
                || permission != COMMAND_ALL && PermissionAPI.getPermission(player, COMMAND_ALL);
    }

    private static PermissionNode<Boolean> node(String path) {
        String name = "sve." + path;
        return new PermissionNode<>(
                ResourceLocation.fromNamespaceAndPath("sve", path),
                PermissionTypes.BOOLEAN,
                (player, playerId, context) -> player != null
                        && player.hasPermissions(SveWorldData.get(player.serverLevel()).permissionLevel(name)));
    }
}
