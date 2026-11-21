package dev.stormclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public final class BaseFinder {
    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static Waypoint navigationTarget;

    private BaseFinder() {}

    public static void handleChatCommand(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        String command = message.toLowerCase(Locale.ROOT).trim();
        if (command.equals("#base clear")) {
            WAYPOINTS.clear();
            navigationTarget = null;
            tell("Cleared saved base markers.");
            return;
        }
        if (command.startsWith("#base tp")) {
            if (WAYPOINTS.isEmpty()) {
                tell("No saved base markers. Run #base first.");
                return;
            }
            int index = 0;
            String[] parts = command.split("\\s+");
            if (parts.length >= 3) {
                try { index = Integer.parseInt(parts[2]) - 1; }
                catch (NumberFormatException ignored) {
                    tell("Usage: #base tp [number]");
                    return;
                }
            }
            if (index < 0 || index >= WAYPOINTS.size()) {
                tell("That base number does not exist. Use #base list.");
                return;
            }
            navigationTarget = WAYPOINTS.get(index);
            tell("Navigation target set to " + navigationTarget.name + " at "
                    + navigationTarget.x + " " + navigationTarget.y + " " + navigationTarget.z
                    + ". Follow the coordinates in your HUD; this does not teleport you.");
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
                tell((i + 1) + ". " + point.name + " at " + point.x + " " + point.y + " " + point.z
                        + " | score " + point.score);
            }
            return;
        }

        if (!command.equals("#base")) return;
        if (!StormClient.baseFinderEnabled) {
            tell("Base Finder is OFF. Enable it in StormClient > Utility.");
            return;
        }

        tell("Scanning nearby loaded chunks (48-block radius)...");
        Candidate candidate = scan(client.level, client.player.blockPosition());
        if (candidate == null || candidate.score < 5) {
            tell("No likely base activity found. This only checks blocks already loaded by your client.");
            return;
        }

        int x = candidate.x();
        int y = candidate.y();
        int z = candidate.z();
        String name = "Base " + (WAYPOINTS.size() + 1);
        WAYPOINTS.add(new Waypoint(name, x, y, z, candidate.score));
        double distance = Math.sqrt(client.player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
        tell("Possible base: " + name + " at " + x + " " + y + " " + z
                + " | score " + candidate.score + " | " + Math.round(distance) + " blocks away.");
        tell("Saved for this session. Use #base list or #base clear.");
    }

    public static String getNavigationText(Minecraft client) {
        if (navigationTarget == null || client.player == null) return null;
        double dx = navigationTarget.x + 0.5 - client.player.getX();
        double dz = navigationTarget.z + 0.5 - client.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        return String.format(Locale.ROOT, "Base waypoint: %s | %.0fm | X %.0f Z %.0f",
                navigationTarget.name, distance, (double) navigationTarget.x, (double) navigationTarget.z);
    }

    private static Candidate scan(Level level, BlockPos origin) {
        Map<Long, Candidate> byChunk = new HashMap<>();
        int radius = 48;
        int minY = Math.max(level.getMinY(), origin.getY() - 16);
        int maxY = Math.min(level.getMaxY() - 1, origin.getY() + 16);

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                // Check the chunk once per vertical column instead of once per block.
                if (!level.hasChunkAt(new BlockPos(x, minY, z))) continue;

                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                            .getPath().toLowerCase(Locale.ROOT);
                    int weight = weight(blockId);
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
        if (block.equals("ender_chest") || block.equals("chest") || block.equals("trapped_chest")
                || block.equals("barrel") || block.endsWith("_shulker_box")) return 3;
        if (block.equals("crafting_table") || block.equals("furnace") || block.equals("smoker")
                || block.equals("blast_furnace") || block.equals("hopper")) return 2;
        if (block.endsWith("_bed") || block.endsWith("_door") || block.equals("anvil")
                || block.equals("brewing_stand")) return 1;
        return 0;
    }

    private static void tell(String message) {
        Minecraft.getInstance().showDebugChat(Component.literal("[StormClient] " + message));
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
