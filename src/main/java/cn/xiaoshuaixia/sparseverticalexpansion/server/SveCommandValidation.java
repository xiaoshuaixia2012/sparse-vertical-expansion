package cn.xiaoshuaixia.sparseverticalexpansion.server;

import cn.xiaoshuaixia.sparseverticalexpansion.world.ExtendedYRange;
import cn.xiaoshuaixia.sparseverticalexpansion.world.SveWorldData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SveCommandValidation {
    private static final Dynamic3CommandExceptionType ERROR_UNCONFIGURED = new Dynamic3CommandExceptionType(
            (x, y, z) -> Component.translatable("commands.sve.region.unconfigured", x, y, z));

    private SveCommandValidation() {
    }

    public static boolean isInCommandBounds(Level level, BlockPos pos) {
        if (!level.isOutsideBuildHeight(pos)) {
            return level.isInWorldBounds(pos);
        }
        return pos.getX() >= -30_000_000
                && pos.getX() < 30_000_000
                && pos.getZ() >= -30_000_000
                && pos.getZ() < 30_000_000
                && pos.getY() >= ExtendedYRange.STANDARD_MIN_Y
                && pos.getY() <= ExtendedYRange.STANDARD_MAX_Y;
    }

    public static void requireConfigured(ServerLevel level, BlockPos pos) throws CommandSyntaxException {
        if (!isConfigured(level, pos)) {
            throw ERROR_UNCONFIGURED.create(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public static boolean isConfigured(ServerLevel level, BlockPos pos) {
        int y = pos.getY();
        return y >= ExtendedYRange.VANILLA_MIN_Y && y <= ExtendedYRange.VANILLA_MAX_Y
                || SveWorldData.get(level)
                .findRegion(level.dimension().location(), pos.getX() >> 4, pos.getZ() >> 4, y)
                .isPresent();
    }

    public static void requireConfigured(ServerLevel level, BoundingBox area) throws CommandSyntaxException {
        long volume = (long) area.getXSpan() * area.getYSpan() * area.getZSpan();
        if (volume > level.getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            requireConfigured(level, pos);
        }
    }
}
