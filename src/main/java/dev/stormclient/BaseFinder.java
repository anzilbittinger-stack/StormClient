package dev.stormclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Scores loaded chunks for clusters of player-base evidence.
 * This is a heuristic finder, not a guarantee that a player base exists.
 */
public final class BaseFinder {
    private static final List<Candidate> CURRENT_CANDIDATES = new ArrayList<>();
    private static final Map<Long, Candidate> SCANNED_CANDIDATES = new HashMap<>();
    private static final Set<Long> SCANNED_CHUNKS = new HashSet<>();
    private static final Set<Long> ALERTED_CHUNKS = new HashSet<>();
    private static final List<Long> CHUNK_QUEUE = new ArrayList<>();
    private static int scanIndex;
    private static int queuedCenterX = Integer.MIN_VALUE;
    private static int queuedCenterZ = Integer.MIN_VALUE;
    private static int queuedRenderDistance = -1;
    private static long lastWorldTime = Long.MIN_VALUE;

    private BaseFinder() {}

    public static void tick(Minecraft client) {
        if (!StormClient.chunkFinderEnabled || client.player == null || client.level == null) {
            reset();
            return;
        }
        if (client.level.getGameTime() == lastWorldTime) return;
        lastWorldTime = client.level.getGameTime();

        int centerX = client.player.chunkPosition().x();
        int centerZ = client.player.chunkPosition().z();
        int renderDistance = Math.max(2, client.options.renderDistance().get());
        int topY = Math.min(client.level.getMaxY() - 1, client.player.blockPosition().getY());

        if (centerX != queuedCenterX || centerZ != queuedCenterZ
                || renderDistance != queuedRenderDistance) {
            rebuildQueue(centerX, centerZ, renderDistance);
        }

        // Scan one loaded chunk per tick so the finder does not freeze the game.
        if (scanIndex < CHUNK_QUEUE.size()) {
            long key = CHUNK_QUEUE.get(scanIndex++);
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            if (scanChunk(client.level, chunkX, chunkZ, topY)) {
                SCANNED_CHUNKS.add(key);
                // Candidate scores are shown in the HUD; no chat API call here.

            }
            refreshCandidates();
        }
    }

    private static void reset() {
        CURRENT_CANDIDATES.clear();
        SCANNED_CANDIDATES.clear();
        SCANNED_CHUNKS.clear();
        ALERTED_CHUNKS.clear();
        CHUNK_QUEUE.clear();
        scanIndex = 0;
        queuedCenterX = Integer.MIN_VALUE;
        queuedCenterZ = Integer.MIN_VALUE;
        queuedRenderDistance = -1;
        lastWorldTime = Long.MIN_VALUE;
    }

    private static void rebuildQueue(int centerX, int centerZ, int renderDistance) {
        SCANNED_CANDIDATES.keySet().removeIf(key -> !insideRange(key, centerX, centerZ, renderDistance));
        SCANNED_CHUNKS.removeIf(key -> !insideRange(key, centerX, centerZ, renderDistance));
        ALERTED_CHUNKS.removeIf(key -> !insideRange(key, centerX, centerZ, renderDistance));
        CHUNK_QUEUE.clear();
        scanIndex = 0;
        queuedCenterX = centerX;
        queuedCenterZ = centerZ;
        queuedRenderDistance = renderDistance;

        for (int x = centerX - renderDistance; x <= centerX + renderDistance; x++) {
            for (int z = centerZ - renderDistance; z <= centerZ + renderDistance; z++) {
                long key = chunkKey(x, z);
                if (!SCANNED_CHUNKS.contains(key)) CHUNK_QUEUE.add(key);
            }
        }
        refreshCandidates();
    }

    private static boolean scanChunk(Level level, int chunkX, int chunkZ, int topY) {
        int minY = level.getMinY();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        if (!level.hasChunkAt(new BlockPos(startX, topY, startZ))) return false;

        long key = chunkKey(chunkX, chunkZ);
        Candidate candidate = null;
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y <= topY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                            .getPath().toLowerCase(Locale.ROOT);
                    int blockWeight = weight(blockId);
                    if (blockWeight == 0) continue;
                    if (candidate == null) {
                        candidate = new Candidate(chunkX, chunkZ);
                        SCANNED_CANDIDATES.put(key, candidate);
                    }
                    candidate.add(blockId, blockWeight);
                }
            }
        }
        return true;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    private static boolean insideRange(long key, int centerX, int centerZ, int radius) {
        int chunkX = (int) (key >> 32);
        int chunkZ = (int) key;
        return Math.abs((long) chunkX - centerX) <= radius
                && Math.abs((long) chunkZ - centerZ) <= radius;
    }

    private static void refreshCandidates() {
        CURRENT_CANDIDATES.clear();
        CURRENT_CANDIDATES.addAll(SCANNED_CANDIDATES.values().stream()
                .filter(candidate -> candidate.score() >= 22)
                .sorted(Comparator.comparingInt(Candidate::score).reversed())
                .limit(64)
                .toList());
    }

    /** Renders a red chunk frame and a beacon-like locator beam above promising chunks. */
    public static void renderChunkOutlines(LevelRenderContext context) {
        if (!StormClient.chunkFinderEnabled || CURRENT_CANDIDATES.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        double minY = client.level.getMinY();
        double maxY = Math.min(client.level.getMaxY(), client.player.blockPosition().getY() + 1.0);
        double t = 0.075;

        for (Candidate candidate : CURRENT_CANDIDATES) {
            double minX = candidate.chunkX * 16.0;
            double minZ = candidate.chunkZ * 16.0;
            double maxX = minX + 16.0;
            double maxZ = minZ + 16.0;
            int frameColor = candidate.score() >= 55
                    ? ARGB.colorFromFloat(0.95f, 1.0f, 0.08f, 0.04f)
                    : ARGB.colorFromFloat(0.82f, 1.0f, 0.55f, 0.05f);
            int beamColor = ARGB.colorFromFloat(0.62f, 1.0f, 0.12f, 0.08f);
            int surfaceY = client.level.getHeight(Heightmap.Types.WORLD_SURFACE,
                    candidate.chunkX * 16 + 8, candidate.chunkZ * 16 + 8);
            double beamTop = Math.min(client.level.getMaxY(), Math.max(surfaceY + 48.0, maxY));
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, minX + t, maxY, minZ + t), frameColor);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, minZ, maxX, maxY, minZ + t), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, maxZ - t, minX + t, maxY, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, maxZ - t, maxX, maxY, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, maxX, maxY, minZ + t), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, maxZ - t, maxX, maxY, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, minX + t, maxY, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, maxY - t, minZ, maxX, maxY, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, maxX, minY + t, minZ + t), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, maxZ - t, maxX, minY + t, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, minX + t, minY + t, maxZ), frameColor);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, minZ, maxX, minY + t, maxZ), frameColor);
                        // A tall, narrow beam makes the chunk easy to spot from a distance.
                        drawFilledBox(pose, buffer, new AABB(minX + 7.72, surfaceY, minZ + 7.72,
                                minX + 8.28, beamTop, minZ + 8.28), beamColor);
                    });
        }
        poseStack.popPose();
    }

    private static void drawFilledBox(PoseStack.Pose pose,
            com.mojang.blaze3d.vertex.VertexConsumer buffer, AABB box, int color) {
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ);
    }

    public static List<String> getOverlayLines() {
        if (!StormClient.chunkFinderEnabled) return List.of();
        List<String> lines = new ArrayList<>();
        int total = SCANNED_CHUNKS.size() + Math.max(0, CHUNK_QUEUE.size() - scanIndex);
        int done = SCANNED_CHUNKS.size() + scanIndex;
        String state = scanIndex < CHUNK_QUEUE.size() ? "SCANNING" : "SCAN COMPLETE";
        lines.add(String.format(Locale.ROOT, "BASE FINDER: %s | %d / %d chunks",
                state, Math.min(done, total), total));
        lines.add(String.format(Locale.ROOT, "BASE CANDIDATES: %d", CURRENT_CANDIDATES.size()));
        int count = Math.min(6, CURRENT_CANDIDATES.size());
        for (int i = 0; i < count; i++) {
            Candidate candidate = CURRENT_CANDIDATES.get(i);
            lines.add(String.format(Locale.ROOT, "BASE LIKELIHOOD %d/100 (%s) | Chunk %d, %d",
                    candidate.score(), candidate.level(), candidate.chunkX, candidate.chunkZ));
        }
        return lines;
    }

    private static int weight(String block) {
        // Storage is useful evidence, but a single container is deliberately weak.
        if (block.equals("ender_chest")) return 1;
        if (block.equals("chest") || block.equals("trapped_chest") || block.equals("barrel")
                || block.endsWith("_shulker_box")) return 3;
        if (block.equals("spawner")) return 10;
        if (isArtificialLight(block)) return 1;
        if (block.equals("crafting_table") || block.equals("furnace") || block.equals("smoker")
                || block.equals("blast_furnace") || block.equals("hopper") || block.equals("brewing_stand")
                || block.equals("stonecutter") || block.equals("smithing_table") || block.equals("grindstone")
                || block.equals("cartography_table") || block.equals("fletching_table") || block.equals("loom")
                || block.equals("anvil")) return 2;
        if (block.endsWith("_bed") || block.endsWith("_door")) return 1;
        return 0;
    }

    private static boolean isArtificialLight(String block) {
        return block.equals("torch") || block.equals("wall_torch")
                || block.equals("soul_torch") || block.equals("soul_wall_torch")
                || block.equals("lantern") || block.equals("soul_lantern")
                || block.equals("glowstone") || block.equals("sea_lantern")
                || block.equals("redstone_lamp") || block.equals("shroomlight")
                || block.equals("end_rod") || block.equals("jack_o_lantern")
                || block.equals("campfire") || block.equals("soul_campfire")
                || block.equals("ochre_froglight") || block.equals("verdant_froglight")
                || block.equals("pearlescent_froglight") || block.equals("copper_bulb")
                || block.equals("exposed_copper_bulb") || block.equals("weathered_copper_bulb")
                || block.equals("oxidized_copper_bulb") || block.endsWith("_candle")
                || block.endsWith("_wall_candle");
    }

    private static final class Candidate {
        final int chunkX;
        final int chunkZ;
        int storageCount;
        int enderChests;
        int lightCount;
        int utilityCount;
        int spawners;
        final Set<String> storageKinds = new HashSet<>();
        final Set<String> lightKinds = new HashSet<>();

        Candidate(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        void add(String kind, int blockWeight) {
            if (kind.equals("ender_chest") || kind.equals("chest") || kind.equals("trapped_chest")
                    || kind.equals("barrel") || kind.endsWith("_shulker_box")) {
                storageCount++;
                if (kind.equals("ender_chest")) enderChests++;
                storageKinds.add(kind);
            } else if (isArtificialLight(kind)) {
                lightCount++;
                lightKinds.add(kind);
            } else if (kind.equals("spawner")) {
                spawners++;
            } else {
                utilityCount++;
            }
        }

        int score() {
            int normalStorage = storageCount - enderChests;
            int storageScore = normalStorage == 0 ? 0 : Math.min(36, 9 + normalStorage * 7);
            int enderChestScore = enderChests == 0 ? 0 : (normalStorage == 0 ? 3 : 2);
            int varietyScore = Math.min(12, Math.max(0, storageKinds.size() - 1) * 5);
            int lightScore = lightCount < 2 ? 0
                    : Math.min(14, 3 + lightCount * 2 + Math.max(0, lightKinds.size() - 1) * 2);
            int utilityScore = utilityCount < 2 ? utilityCount * 2 : Math.min(18, 4 + utilityCount * 3);
            int spawnerScore = Math.min(30, spawners * 18);
            int combinationBonus = (storageCount > 0 && lightCount >= 2 && utilityCount > 0) ? 10 : 0;
            return Math.min(99, storageScore + enderChestScore + varietyScore
                    + lightScore + utilityScore + spawnerScore + combinationBonus);
        }

        String level() {
            int score = score();
            return score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
        }
    }
}
