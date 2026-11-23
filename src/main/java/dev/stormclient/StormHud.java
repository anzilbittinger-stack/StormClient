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
        // Keep trading status visible in-game instead of hiding it inside the ClickGUI.
        if (StormClient.marketBotEnabled) {
            draw(graphics, "MARKET BOT: " + MarketBot.getStatus(), x, y, 0xFFFFD36A);
            y += line;
        }
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
        if (StormClient.totemAlertEnabled) {
            int totems = client.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) ? 1 : 0;
            for (int i = 0; i < client.player.getInventory().getContainerSize(); i++) {
                var stack = client.player.getInventory().getItem(i);
                if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) totems += stack.getCount();
            }
            if (totems == 0) {
                draw(graphics, "WARNING: NO TOTEMS IN INVENTORY", x, y);
                y += line;
            } else {
                draw(graphics, "Totems available: " + totems, x, y);
                y += line;
            }
        }
        if (StormClient.visiblePlayersEnabled) {
            int shown = 0;
            for (var other : client.level.players()) {
                if (other == client.player || !other.isAlive() || !client.player.hasLineOfSight(other)) continue;
                double distance = client.player.distanceTo(other);
                if (distance > 96.0) continue;
                draw(graphics, String.format(java.util.Locale.ROOT, "Player: %s (%.0fm)",
                        other.getName().getString(), distance), x, y);
                y += line;
                if (++shown >= 5) break;
            }
        }
        if (StormClient.visibleMobsEnabled) {
            int shown = 0;
            var box = client.player.getBoundingBox().inflate(64.0);
            for (var mob : client.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                    entity -> entity.isAlive() && client.player.hasLineOfSight(entity))) {
                double distance = client.player.distanceTo(mob);
                draw(graphics, String.format(java.util.Locale.ROOT, "Mob: %s (%.0fm)",
                        mob.getType().getDescription().getString(), distance), x, y);
                y += line;
                if (++shown >= 5) break;
            }
        }
        for (String baseLine : BaseFinder.getOverlayLines()) {
            draw(graphics, baseLine, x, y, 0xFFFF4040);
            y += line;
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
        draw(graphics, text, x, y, 0xFFF2F5FA);
    }

    private static void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(Minecraft.getInstance().font, Component.literal(text), x, y, color, true);
    }
}
