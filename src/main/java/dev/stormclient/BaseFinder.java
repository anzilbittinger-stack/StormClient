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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Heuristic finder for base-like blocks that are exposed to the player's normal view.
 * It deliberately does not inspect concealed storage through solid terrain.
 */
public final class BaseFinder {
    private static final List<Candidate> CURRENT_CANDIDATES = new ArrayList<>();
    private static int scanCooldown;
    private static long lastWorldTime = Long.MIN_VALUE;

    private BaseFinder() {}

    public static void tick(Minecraft client) {
        if (!StormClient.chunkFinderEnabled || client.player == null || client.level == null) {
            CURRENT_CANDIDATES.clear();
            scanCooldown = 0;
            lastWorldTime = Long.MIN_VALUE;
            return;
        }
        if (client.level.getGameTime() == lastWorldTime) return;
        lastWorldTime = client.level.getGameTime();
        if (scanCooldown > 0) {
            scanCooldown--;
            return;
        }
        CURRENT_CANDIDATES.clear();
        CURRENT_CANDIDATES.addAll(scan(client.level, client.player));
        scanCooldown = 40;
    }


    /**
     * Draws a full-height 3D red frame around each candidate chunk.
     * The frame runs from the world's top build limit to its bottom build limit.
     */
    public static void renderChunkOutlines(LevelRenderContext context) {
        if (!StormClient.chunkFinderEnabled || CURRENT_CANDIDATES.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        int red = ARGB.colorFromFloat(0.92f, 1.0f, 0.025f, 0.025f);
        double minY = client.level.getMinY();
        double maxY = client.level.getMaxY();
        double t = 0.075;
        for (Candidate candidate : CURRENT_CANDIDATES) {
            double minX = candidate.chunkX * 16.0;
            double minZ = candidate.chunkZ * 16.0;
            double maxX = minX + 16.0;
            double maxZ = minZ + 16.0;
            context.submitNodeCollector().submitCustomGeometry(
                    poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                        // Four full-height vertical corner edges.
                        drawFilledBox(pose, buffer, new AABB(minX, minY, minZ, minX + t, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, minZ, maxX, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(minX, minY, maxZ - t, minX + t, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, minY, maxZ - t, maxX, maxY, maxZ), red);
                        // Top perimeter.
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, maxX, maxY, minZ + t), red);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, maxZ - t, maxX, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(minX, maxY - t, minZ, minX + t, maxY, maxZ), red);
                        drawFilledBox(pose, buffer, new AABB(maxX - t, maxY - t, minZ, maxX, maxY, maxZ), red);
                        // Bottom perimeter, at the bedrock/build-limit end.
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
        // Front and back faces
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
        // Left and right faces
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.minX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.minZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.minY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.maxZ).setColor(color);
        buffer.addVertex(pose, (float) box.maxX, (float) box.maxY, (float) box.minZ).setColor(color);
        // Top and bottom faces
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
        int count = Math.min(5, CURRENT_CANDIDATES.size());
        for (int i = 0; i < count; i++) {
            Candidate candidate = CURRENT_CANDIDATES.get(i);
            lines.add(String.format(Locale.ROOT, "CHUNK FINDER: %d%% | Chunk %d, %d",
                    candidate.likelihood(), candidate.chunkX, candidate.chunkZ));
        }
        return lines;
    }

    private static List<Candidate> scan(Level level, net.minecraft.world.entity.player.Player player) {
        Map<Long, Candidate> byChunk = new HashMap<>();
        BlockPos origin = player.blockPosition();
        int radius = 48;
        int minY = Math.max(level.getMinY(), origin.getY() - 16);
        int maxY = Math.min(level.getMaxY() - 1, origin.getY() + 16);
        Vec3 eye = player.getEyePosition();

        for (int x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (int z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                if (!level.hasChunkAt(new BlockPos(x, minY, z))) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                            .getPath().toLowerCase(Locale.ROOT);
                    int weight = weight(blockId);
                    if (weight == 0 || !hasExposedFace(level, pos)) continue;

                    Vec3 target = Vec3.atCenterOf(pos);
                    BlockHitResult hit = level.clip(new ClipContext(
                            eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) continue;

                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                    Candidate current = byChunk.computeIfAbsent(key, ignored -> new Candidate(chunkX, chunkZ));
                    current.add(pos, blockId, weight);
                }
            }
        }

        return byChunk.values().stream()
                .filter(candidate -> candidate.likelihood() >= 40)
                .sorted(Comparator.comparingInt(Candidate::likelihood).reversed())
                .limit(12)
                .toList();
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

    private static void tell(String message) {
        Minecraft.getInstance().showDebugChat(Component.literal("[StormClient] " + message));
    }

    private static final class Candidate {
        final int chunkX;
        final int chunkZ;
        int totalX;
        int totalY;
        int totalZ;
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
            totalX += pos.getX();
            totalY += pos.getY();
            totalZ += pos.getZ();
        }

        int likelihood() {
            // A deliberately conservative heuristic, not a guarantee that a player base exists.
            return Math.min(99, count * 9 + kinds.size() * 7 + Math.max(0, weight - count) * 3);
        }

        int centerX() { return totalX / Math.max(1, count); }
        int centerY() { return totalY / Math.max(1, count); }
        int centerZ() { return totalZ / Math.max(1, count); }
    }

}
