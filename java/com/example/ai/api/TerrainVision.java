package com.example.ai.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;


public final class TerrainVision {

    private static final int GROUND_MAP_RADIUS = 4;
    private static final int FORWARD_SCAN_DISTANCE = 10;
    private static final int OBSTACLE_SCAN_DISTANCE = 8;
    private static final int HEADROOM_CHECK_HEIGHT = 4;

    private TerrainVision() {
    }

    public static void appendReport(StringBuilder info, ServerLevel world, BlockPos origin, float yaw, float pitch) {
        info.append("\n");
        appendOrientation(info, yaw, pitch);
        info.append("\n");
        appendGroundHeightMap(info, world, origin);
        info.append("\n");
        appendForwardScan(info, world, origin, yaw);
        info.append("\n");
        appendForwardObstacles(info, world, origin, yaw);
    }

    private static void appendOrientation(StringBuilder info, float yaw, float pitch) {
        int forwardX = forwardX(yaw, 1);
        int forwardZ = forwardZ(yaw, 1);

        info.append("Player orientation:\n");
        info.append("- Facing: ").append(compassFromYaw(yaw))
            .append(" (yaw=").append(formatFloat(yaw))
            .append(", pitch=").append(formatFloat(pitch)).append(")\n");
        info.append("- Forward (1 block ahead, relative): (x=").append(forwardX)
            .append(", z=").append(forwardZ).append(")\n");
        info.append("- Axes: +X=east, +Z=south; build in front using positive distance along forward\n");
    }

    private static void appendGroundHeightMap(StringBuilder info, ServerLevel world, BlockPos origin) {
        info.append("Ground height map (relative surface Y; rows=z, cols=x; P=player column):\n");
        info.append(" z\\x ");
        for (int x = -GROUND_MAP_RADIUS; x <= GROUND_MAP_RADIUS; x++) {
            info.append(String.format("%4d", x));
        }
        info.append('\n');

        for (int z = -GROUND_MAP_RADIUS; z <= GROUND_MAP_RADIUS; z++) {
            info.append(String.format("%4d ", z));
            for (int x = -GROUND_MAP_RADIUS; x <= GROUND_MAP_RADIUS; x++) {
                if (x == 0 && z == 0) {
                    info.append("   P");
                } else {
                    info.append(String.format("%4d", sampleGroundLevel(world, origin, x, z)));
                }
            }
            info.append('\n');
        }
    }

    private static void appendForwardScan(StringBuilder info, ServerLevel world, BlockPos origin, float yaw) {
        info.append("Forward terrain scan (center of view):\n");
        for (int distance = 1; distance <= FORWARD_SCAN_DISTANCE; distance++) {
            int x = forwardX(yaw, distance);
            int z = forwardZ(yaw, distance);
            int groundY = sampleGroundLevel(world, origin, x, z);
            int headroom = countHeadroom(world, origin, x, z, groundY);
            boolean blocked = isBlockedAtBodyHeight(world, origin, x, z, groundY);

            info.append("- d=").append(distance)
                .append(" rel(").append(x).append(",").append(z).append(")")
                .append(" groundY=").append(groundY)
                .append(" headroom=").append(headroom)
                .append(blocked ? " BLOCKED" : " clear")
                .append('\n');
        }
    }

    private static void appendForwardObstacles(StringBuilder info, ServerLevel world, BlockPos origin, float yaw) {
        info.append("Obstacles ahead (solids above ground within ").append(OBSTACLE_SCAN_DISTANCE).append(" blocks):\n");

        int found = 0;
        for (int distance = 1; distance <= OBSTACLE_SCAN_DISTANCE; distance++) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                int x = forwardX(yaw, distance) + lateralX(yaw, lateral);
                int z = forwardZ(yaw, distance) + lateralZ(yaw, lateral);
                int groundY = sampleGroundLevel(world, origin, x, z);

                for (int y = groundY + 1; y <= groundY + HEADROOM_CHECK_HEIGHT; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                        continue;
                    }

                    String blockName = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString();
                    if (blockName.contains("air")) {
                        continue;
                    }

                    info.append("- rel(").append(x).append(",").append(y).append(",").append(z)
                        .append(") d=").append(distance)
                        .append(" : ").append(shortBlockName(blockName))
                        .append('\n');
                    found++;
                    if (found >= 12) {
                        info.append("- ... more obstacles omitted\n");
                        return;
                    }
                    break;
                }
            }
        }

        if (found == 0) {
            info.append("- none detected\n");
        }
    }

    public static int sampleGroundLevel(ServerLevel world, BlockPos origin, int x, int z) {
        BlockPos column = origin.offset(x, 0, z);
        int bottomY = world.getMinY();

        for (int y = origin.getY(); y >= bottomY; y--) {
            BlockPos pos = column.atY(y);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                return y - origin.getY();
            }
        }

        return 0;
    }

    public static int countHeadroom(ServerLevel world, BlockPos origin, int x, int z, int groundY) {
        int headroom = 0;
        for (int y = groundY + 1; y <= groundY + HEADROOM_CHECK_HEIGHT; y++) {
            BlockPos pos = origin.offset(x, y, z);
            if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                headroom++;
            } else {
                break;
            }
        }
        return headroom;
    }

    public static boolean isBlockedAtBodyHeight(ServerLevel world, BlockPos origin, int x, int z, int groundY) {
        for (int y = Math.max(groundY + 1, 1); y <= 2; y++) {
            BlockPos pos = origin.offset(x, y, z);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String shortBlockName(String blockName) {
        int colon = blockName.indexOf(':');
        return colon >= 0 ? blockName.substring(colon + 1) : blockName;
    }

    public static String compassFromYaw(float yaw) {
        float normalized = Math.floorMod(Math.round(yaw), 360);
        if (normalized > 180) {
            normalized -= 360;
        }
        if (normalized >= -45 && normalized < 45) {
            return "SOUTH";
        }
        if (normalized >= 45 && normalized < 135) {
            return "WEST";
        }
        if (normalized >= 135 || normalized < -135) {
            return "NORTH";
        }
        return "EAST";
    }

    public static int forwardX(float yaw, int distance) {
        return (int) Math.round(-Math.sin(Math.toRadians(yaw)) * distance);
    }

    public static int forwardZ(float yaw, int distance) {
        return (int) Math.round(Math.cos(Math.toRadians(yaw)) * distance);
    }

    private static int lateralX(float yaw, int lateral) {
        return (int) Math.round(Math.cos(Math.toRadians(yaw)) * lateral);
    }

    private static int lateralZ(float yaw, int lateral) {
        return (int) Math.round(Math.sin(Math.toRadians(yaw)) * lateral);
    }

    private static String formatFloat(float value) {
        return String.format("%.1f", value);
    }
}
