package dev.stormclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class BaseFinder {
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();

    private BaseFinder() {}

    public static void handleChatCommand(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String command = message.toLowerCase(Locale.ROOT).trim();
        if (command.equals("#base clear")) {
            WAYPOINTS.clear();
            tell("Cleared saved base markers.");
            return;
        }
        if (command.equals("#base list")) {
            if (WAYPOINTS.isEmpty()) {
                tell("No saved bases. Use #base to scan nearby loaded chunks.");
                return;
            }
            tell("Saved base markers:");
            for (int i = 0; i < WAYPOINTS.size(); i++) {
                Waypoint point = WAYPOINTS.get(i);
                tell((i + 1) + ". " + point.name + " at " + point.x + " " + point.y + " " + point.z);
            }
            return;
        }

        if (!StormClient.baseFinderEnabled) {
            tell("Base Finder is OFF. Enable it in StormClient > Utility.");
            return;
        }

        tell("Scanning nearby loaded chunks for possible base activity...");
        Candidate candidate = scan(client.level, client.player.blockPosition());
        if (candidate == null || candidate.score < 5) {
            tell("No convincing base candidate found nearby. Only loaded chunks can be scanned.");
            return;
        }

        int x = candidate.x();
        int y = candidate.y();
        int z = candidate.z();
        String name = "Base " + (WAYPOINTS.size() + 1);
        WAYPOINTS.add(new Waypoint(name, x, y, z, candidate.score));
        double distance = Math.sqrt(client.player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
        tell("Possible base found: " + name + " at " + x + " " + y + " " + z
                + " | score " + candidate.score + " | " + Math.round(distance) + " blocks away.");
        tell("Marker saved for this session. Use #base list or #base clear.");
    }

    private static Candidate scan(Level level, BlockPos origin) {
        Map<Long, Candidate> byChunk = new HashMap<>();
        int radius = 48;
        int minY = Math.max(level.getMinY(), origin.getY() - 16);
        int maxY = Math.min(level.getMaxY() - 1, origin.getY() + 16);

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;

                    String block = level.getBlockState(pos).toString().toLowerCase(Locale.ROOT);
                    int weight = weight(block);
                    if (weight == 0) continue;

                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                    Candidate current = byChunk.computeIfAbsent(key, ignored -> new Candidate());
                    current.score += weight;
                    current.totalX += x * weight;
                    current.totalY += y * weight;
                    current.totalZ += z * weight;
                    current.weight += weight;
                }
            }
        }

        return byChunk.values().stream()
                .filter(candidate -> candidate.score >= 5)
                .max(Comparator.comparingInt(candidate -> candidate.score))
                .orElse(null);
    }

    private static int weight(String block) {
        if (block.contains("ender_chest") || block.contains("chest") || block.contains("barrel")
                || block.contains("shulker_box")) return 3;
        if (block.contains("crafting_table") || block.contains("furnace")
                || block.contains("smoker") || block.contains("blast_furnace")
                || block.contains("hopper")) return 2;
        if (block.contains("_bed") || block.contains("_door") || block.contains("anvil")
                || block.contains("brewing_stand")) return 1;
        return 0;
    }

    private static void tell(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.setOverlayMessage(Component.literal("[StormClient] " + message), false);
        }
    }

    private static final class Candidate {
        int score;
        int totalX;
        int totalY;
        int totalZ;
        int weight;

        int x() { return totalX / Math.max(1, weight); }
        int y() { return totalY / Math.max(1, weight); }
        int z() { return totalZ / Math.max(1, weight); }
    }

    private record Waypoint(String name, int x, int y, int z, int score) {}
}
