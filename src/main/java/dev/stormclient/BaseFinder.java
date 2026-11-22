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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Finds clusters of base-like blocks that are exposed to the player's normal view.
 * It never searches concealed storage through solid terrain.
 */
public final class BaseFinder {
    private static final List<Candidate> CURRENT_CANDIDATES = new ArrayList<>();
    private static final Map<Long, Candidate> SCANNED_CANDIDATES = new HashMap<>();
    private static final List<Long> CHUNK_QUEUE = new ArrayList<>();
    private static int scanIndex;
    private static int queuedCenterX = Integer.MIN_VALUE;
    private static int queuedCenterZ = Integer.MIN_VALUE;
    private static int queuedRenderDistance = -1;
    private static int queuedTopY;
    private static long lastWorldTime = Long.MIN_VALUE;

    private BaseFinder() {}

    public static void tick(Minecraft client) {
        if (!StormClient.chunkFinderEnabled || client.player == null || client.level == null) {
            reset();
            return;
        }
        if (client.level.getGameTime() == lastWorldTime) return;
        lastWorldTime = client.level.getGameTime();

        int centerX = client.player.chunkPosition().x;
        int centerZ = client.player.chunkPosition().z;
        int renderDistance = Math.max(2, client.options.renderDistance().get());
        int topY = Math.min(client.level.getMaxY() - 1, client.player.blockPosition().getY());

        if (centerX != queuedCenterX || centerZ != queuedCenterZ
                || renderDistance != queuedRenderDistance || topY != queuedTopY
                || CHUNK_QUEUE.isEmpty() || scanIndex >= CHUNK_QUEUE.size()) {
            rebuildQueue(client, centerX, centerZ, renderDistance, topY);
        }

        // Scan one already-loaded chunk per client tick to avoid freezing the game.
        if (scanIndex < CHUNK_QUEUE.size()) {
            long key = CHUNK_QUEUE.get(scanIndex++);
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            scanChunk(client.level, client.player, chunkX, chunkZ, topY);
            refreshCandidates();
        }
    }

    private static void reset() {
        CURRENT_CANDIDATES.clear();
        SCANNED_CANDIDATES.clear();
        CHUNK_QUEUE.clear();
        scanIndex = 0;
        queuedCenterX = Integer.MIN_VALUE;
        queuedCenterZ = Integer.MIN_VALUE;
        queuedRenderDistance = -1;
        queuedTopY = 0;
        lastWorldTime = Long.MIN_VALUE;
    }

    private static void rebuildQueue(Minecraft client, int centerX, int centerZ,
            int renderDistance, int topY) {
        CHUNK_QUEUE.clear();
        SCANNED_CANDIDATES.clear();
        CURRENT_CANDIDATES.clear();
        scanIndex = 0;
        queuedCenterX = centerX;
        queuedCenterZ = centerZ;
        queuedRenderDistance = renderDistance;
        queuedTopY = topY;

        // Include every chunk coordinate in the configured render-distance square.
        for (int x = centerX - renderDistance; x <= centerX + renderDistance; x++) {
            for (int z = centerZ - renderDistance; z <= centerZ + renderDistance; z++) {
                CHUNK_QUEUE.add((((long) x) << 32) | (z & 0xffffffffL));
            }
        }
    }

    private static void scanChunk(Level level, Player player, int chunkX, int chunkZ, int topY) {
        int minY = level.getMinY();
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        if (!level.hasChunkAt(new BlockPos(startX, topY, startZ))) return;

        Vec3 eye = player.getEyePosition();
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = minY; y <= topY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                            .getPath().toLowerCase(Locale.ROOT);
                    int blockWeight = weight(blockId);
                    if (blockWeight == 0 || !hasExposedFace(level, pos)) continue;

                    Vec3 target = Vec3.atCenterOf(pos);
                    BlockHitResult hit = level.clip(new ClipContext(
                            eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;

                    long key = (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
                    Candidate candidate = SCANNED_CANDIDATES.computeIfAbsent(
                            key, ignored -> new Candidate(chunkX, chunkZ));
                    candidate.add(pos, blockId, blockWeight);
                }
            }
        }
    }

    private static void refreshCandidates() {
        CURRENT_CANDIDATES.clear();
        CURRENT_CANDIDATES.addAll(SCANNED_CANDIDATES.values().stream()
                .filter(candidate -> candidate.score() > 10)
                .sorted(Comparator.comparingInt(Candidate::score).reversed())
                .limit(64)
                .toList());
    }

    /** Draws visible red chunk frames from the player's height down to the bottom build limit. */
    public static void renderChunkOutlines(LevelRenderContext context) {
        if (!StormClient.chunkFinderEnabled || CURRENT_CANDIDATES.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        int red = ARGB.colorFromFloat(0.92f, 1.0f, 0.025f, 0.025f);
        double minY = client.level.getMinY();
        double maxY = Math.min(client.level.getMaxY(), client.player.blockPosition().getY() + 1.0);
        double t = 0.075;

        for (Candidate candidate : CURRENT_CANDIDATES) {
            double minX = candidate.chunkX * 16.0;
            double minZ = candidate.chunkZ * 16.0;
            double maxX = minX + 16.0;
            double maxZ = minZ + 16.0;
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, minX + t, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, minZ, maxX, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, maxZ - t, minX + t, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, maxZ - t, maxX, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, maxX, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, maxZ - t, maxX, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, minX + t, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, maxY - t, minZ, maxX, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, maxX, minY + t, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, maxZ - t, maxX, minY + t, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, minX + t, minY + t, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, minZ, maxX, minY + t, maxZ), red);
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
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
    }

    public static List<String> getOverlayLines() {
        if (!StormClient.chunkFinderEnabled || CURRENT_CANDIDATES.isEmpty()) return List.of();
        List<String> lines = new ArrayList<>();
        int count = Math.min(8, CURRENT_CANDIDATES.size());
        for (int i = 0; i < count; i++) {
            Candidate candidate = CURRENT_CANDIDATES.get(i);
            lines.add(String.format(Locale.ROOT, "BASE %d%% | Chunk %d, %d",
                    candidate.score(), candidate.chunkX, candidate.chunkZ));
        }
        return lines;
    }

    private static boolean hasExposedFace(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).isAir()
                || level.getBlockState(pos.below()).isAir()
                || level.getBlockState(pos.north()).isAir()
                || level.getBlockState(pos.south()).isAir()
                || level.getBlockState(pos.east()).isAir()
                || level.getBlockState(pos.west()).isAir();
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

    private static final class Candidate {
        final int chunkX;
        final int chunkZ;
        int count;
        int weight;
        final Set<String> kinds = new HashSet<>();

        Candidate(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        void add(BlockPos pos, String kind, int blockWeight) {
            count++;
            weight += blockWeight;
            kinds.add(kind);
        }

        int score() {
            // Evidence score, not a statistically calibrated probability.
            return Math.min(99, count * 9 + kinds.size() * 7 + Math.max(0, weight - count) * 3);
        }
    }
}
