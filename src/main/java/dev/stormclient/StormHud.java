package dev.stormclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Small client-side information HUD. It only displays information already available to the player. */
public final class StormHud {
    private StormHud() {}

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(StormClient.MOD_ID, "information_hud"),
                StormHud::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        int x = 8;
        int y = 8;
        int line = 11;
        if (StormClient.hudFpsEnabled) {
            draw(graphics, "FPS: " + client.getFps(), x, y);
            y += line;
        }
        if (StormClient.hudCoordinatesEnabled) {
            var pos = client.player.blockPosition();
            draw(graphics, "XYZ: " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ(), x, y);
            y += line;
        }
        if (StormClient.hudDirectionEnabled) {
            String direction = client.player.getDirection().getName().toUpperCase(java.util.Locale.ROOT);
            draw(graphics, "Facing: " + direction, x, y);
            y += line;
        }
        if (StormClient.hudHeldItemEnabled) {
            String item = client.player.getMainHandItem().isEmpty()
                    ? "Empty hand" : client.player.getMainHandItem().getHoverName().getString();
            draw(graphics, "Held: " + item, x, y);
            y += line;
        }
        if (StormClient.hudArmorEnabled) {
            int armor = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (!client.player.getItemBySlot(slot).isEmpty()) armor++;
            }
            draw(graphics, "Armor pieces: " + armor + "/4", x, y);
            y += line;
        }
        if (StormClient.hudActiveModulesEnabled) {
            String modules = "Modules: "
                    + (StormClient.airJumpEnabled ? "AirJump " : "")
                    + (StormClient.autoSprintEnabled ? "Sprint " : "")
                    + (StormClient.fullbrightEnabled ? "Fullbright " : "")
                    + (StormClient.elytraAssistEnabled ? "Elytra " : "");
            draw(graphics, modules.equals("Modules: ") ? "Modules: none" : modules, x, y);
            y += line;
        }
        if (StormClient.hudVitalsEnabled) {
            float health = client.player.getHealth();
            float maxHealth = client.player.getMaxHealth();
            int food = client.player.getFoodData().getFoodLevel();
            draw(graphics, String.format(java.util.Locale.ROOT,
                    "HP: %.1f/%.1f | Food: %d/20", health, maxHealth, food), x, y);
            y += line;
        }
        if (StormClient.hudPingEnabled && client.getConnection() != null) {
            var playerInfo = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (playerInfo != null) {
                draw(graphics, "Ping: " + playerInfo.getLatency() + " ms", x, y);
                y += line;
            }
        }
        if (StormClient.hudWorldTimeEnabled) {
            long ticks = Math.floorMod(client.level.getDayTime(), 24000L);
            int hours = (int) ((ticks / 1000L + 6L) % 24L);
            int minutes = (int) ((ticks % 1000L) * 60L / 1000L);
            draw(graphics, String.format(java.util.Locale.ROOT, "World time: %02d:%02d", hours, minutes), x, y);
            y += line;
        }
        if (StormClient.hudHeldDurabilityEnabled) {
            var held = client.player.getMainHandItem();
            if (!held.isEmpty() && held.getMaxDamage() > 0) {
                int remaining = held.getMaxDamage() - held.getDamageValue();
                draw(graphics, "Held durability: " + remaining + "/" + held.getMaxDamage(), x, y);
                y += line;
            }
        }
        if (StormClient.hudArmorDurabilityEnabled) {
            StringBuilder armorState = new StringBuilder("Armor durability: ");
            boolean found = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var stack = client.player.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.getMaxDamage() > 0) {
                    if (found) armorState.append(" | ");
                    armorState.append(slot.getName()).append(" ")
                            .append(stack.getMaxDamage() - stack.getDamageValue())
                            .append("/").append(stack.getMaxDamage());
                    found = true;
                }
            }
            if (found) {
                draw(graphics, armorState.toString(), x, y);
                y += line;
            }
        }
        if (StormClient.hudMaceEnabled) {
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(client.player.getMainHandItem().getItem()).getPath();
            if (itemId.equals("mace")) {
                double verticalSpeed = client.player.getDeltaMovement().y;
                String state = client.player.onGround() ? "GROUNDED"
                        : verticalSpeed < -0.08 ? "FALLING" : "AIRBORNE";
                draw(graphics, String.format(java.util.Locale.ROOT,
                        "MACE: %s | Y speed: %.2f", state, verticalSpeed), x, y);
            }
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, Component.literal(text), x, y, 0xFFF2F5FA, true);
    }
}
