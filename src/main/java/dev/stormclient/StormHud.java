package dev.stormclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        if (client.player == null || client.level == null || client.screen != null) return;

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
            for (var stack : client.player.getArmorSlots()) {
                if (!stack.isEmpty()) armor++;
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
        }
    }

    private static void draw(GuiGraphicsExtractor graphics, String text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, Component.literal(text), x, y, 0xFFF2F5FA, true);
    }
}
