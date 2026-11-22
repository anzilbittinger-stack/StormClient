package dev.stormclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Independently implemented Astran-inspired StormClient menu.
 * Search, category filtering, module toggles and presets are handled locally.
 */
public final class StormClickGuiScreen extends Screen {
    private enum Tab { MODULES, COMBOS, HUD }
    private enum Category { COMBAT, MOVEMENT, VISUALS, PLAYER, WORLD, UTILITY }

    private Tab tab = Tab.MODULES;
    private Category category = Category.MOVEMENT;
    private String search = "";
    private int scroll;
    private static final int PANEL_W = 520;
    private static final int PANEL_H = 370;
    private static final int ROW_H = 31;

    public StormClickGuiScreen() {
        super(Component.literal("StormClient"));
    }

    private int left() { return (width - PANEL_W) / 2; }
    private int top() { return Math.max(6, (height - PANEL_H) / 2); }

    private record ModuleEntry(String name, String description,
                               BooleanSupplier enabled, Runnable toggle) {}

    private static ModuleEntry entry(String name, String description,
                                     BooleanSupplier enabled, Runnable toggle) {
        return new ModuleEntry(name, description, enabled, toggle);
    }

    private List<ModuleEntry> modules() {
        List<ModuleEntry> all = new ArrayList<>();
        switch (category) {
            case COMBAT -> all.add(entry("Mace PvP HUD", "Display-only mace state and vertical speed",
                    () -> StormClient.hudMaceEnabled, () -> StormClient.hudMaceEnabled = !StormClient.hudMaceEnabled));
            case MOVEMENT -> {
                all.add(entry("Air Jump", "Client-side extra jump input", () -> StormClient.airJumpEnabled,
                        () -> StormClient.airJumpEnabled = !StormClient.airJumpEnabled));
                all.add(entry("Auto Sprint", "Sprint while moving forward", () -> StormClient.autoSprintEnabled,
                        () -> StormClient.autoSprintEnabled = !StormClient.autoSprintEnabled));
                all.add(entry("Elytra Assist", "Gently adjusts pitch while gliding", () -> StormClient.elytraAssistEnabled,
                        () -> StormClient.elytraAssistEnabled = !StormClient.elytraAssistEnabled));
            }
            case VISUALS -> {
                all.add(entry("Fullbright", "Raises local client gamma", () -> StormClient.fullbrightEnabled,
                        () -> StormClient.fullbrightEnabled = !StormClient.fullbrightEnabled));
                all.add(entry("Visible Player Indicators", "Lists nearby players in line of sight only",
                        () -> StormClient.visiblePlayersEnabled, () -> StormClient.visiblePlayersEnabled = !StormClient.visiblePlayersEnabled));
                all.add(entry("Visible Mob Indicators", "Lists nearby mobs in line of sight only",
                        () -> StormClient.visibleMobsEnabled, () -> StormClient.visibleMobsEnabled = !StormClient.visibleMobsEnabled));
                all.add(entry("Zoom", "Hold C to zoom; keybind is currently fixed",
                        () -> false, () -> {}));
            }
            case PLAYER -> {
                all.add(entry("Auto Totem", "Moves a carried totem into an empty offhand",
                        () -> StormClient.autoTotemEnabled, () -> StormClient.autoTotemEnabled = !StormClient.autoTotemEnabled));
                all.add(entry("Auto Armor", "Equips matching armor from the hotbar into empty armor slots",
                        () -> StormClient.autoArmorEnabled, () -> StormClient.autoArmorEnabled = !StormClient.autoArmorEnabled));
                all.add(entry("Auto Respawn", "Respawns when the death screen is open",
                        () -> StormClient.autoRespawnEnabled, () -> StormClient.autoRespawnEnabled = !StormClient.autoRespawnEnabled));
                all.add(entry("Totem Alert", "Shows the number of carried totems",
                        () -> StormClient.totemAlertEnabled, () -> StormClient.totemAlertEnabled = !StormClient.totemAlertEnabled));
            }
            case WORLD -> all.add(entry("Chunk Finder", "Automatically marks suspicious chunks with full-height 3D frames",
                    () -> StormClient.chunkFinderEnabled, () -> StormClient.chunkFinderEnabled = !StormClient.chunkFinderEnabled));
            case UTILITY -> {
                all.add(entry("Market Bot", "Auto-buys qualifying AH listings and relists flips on Donut-style servers",
                        () -> StormClient.marketBotEnabled, () -> StormClient.marketBotEnabled = !StormClient.marketBotEnabled));
            }
        }
        if (!search.isBlank()) {
            String query = search.toLowerCase(Locale.ROOT);
            all.removeIf(module -> !module.name().toLowerCase(Locale.ROOT).contains(query)
                    && !module.description().toLowerCase(Locale.ROOT).contains(query));
        }
        return all;
    }

    private List<ModuleEntry> hudModules() {
        List<ModuleEntry> all = new ArrayList<>();
        all.add(entry("FPS Counter", "Displays current client FPS", () -> StormClient.hudFpsEnabled, () -> StormClient.hudFpsEnabled = !StormClient.hudFpsEnabled));
        all.add(entry("Coordinates", "Displays block coordinates", () -> StormClient.hudCoordinatesEnabled, () -> StormClient.hudCoordinatesEnabled = !StormClient.hudCoordinatesEnabled));
        all.add(entry("Direction", "Displays facing direction", () -> StormClient.hudDirectionEnabled, () -> StormClient.hudDirectionEnabled = !StormClient.hudDirectionEnabled));
        all.add(entry("Held Item", "Displays main-hand item name", () -> StormClient.hudHeldItemEnabled, () -> StormClient.hudHeldItemEnabled = !StormClient.hudHeldItemEnabled));
        all.add(entry("Armor", "Displays equipped armor piece count", () -> StormClient.hudArmorEnabled, () -> StormClient.hudArmorEnabled = !StormClient.hudArmorEnabled));
        all.add(entry("Active Modules", "Displays enabled movement and visual modules", () -> StormClient.hudActiveModulesEnabled, () -> StormClient.hudActiveModulesEnabled = !StormClient.hudActiveModulesEnabled));
        all.add(entry("Mace HUD", "Displays grounded/falling/airborne state", () -> StormClient.hudMaceEnabled, () -> StormClient.hudMaceEnabled = !StormClient.hudMaceEnabled));
        all.add(entry("Health + Hunger", "Displays current health and food level", () -> StormClient.hudVitalsEnabled, () -> StormClient.hudVitalsEnabled = !StormClient.hudVitalsEnabled));
        all.add(entry("Ping", "Displays server latency when available", () -> StormClient.hudPingEnabled, () -> StormClient.hudPingEnabled = !StormClient.hudPingEnabled));
        all.add(entry("Held Item Durability", "Displays main-hand durability", () -> StormClient.hudHeldDurabilityEnabled, () -> StormClient.hudHeldDurabilityEnabled = !StormClient.hudHeldDurabilityEnabled));
        all.add(entry("Armor Durability", "Displays equipped armor durability", () -> StormClient.hudArmorDurabilityEnabled, () -> StormClient.hudArmorDurabilityEnabled = !StormClient.hudArmorDurabilityEnabled));
        if (!search.isBlank()) {
            String query = search.toLowerCase(Locale.ROOT);
            all.removeIf(module -> !module.name().toLowerCase(Locale.ROOT).contains(query)
                    && !module.description().toLowerCase(Locale.ROOT).contains(query));
        }
        return all;
    }

    private void applyPreset(int preset) {
        // Presets only configure existing, safe client-side options.
        if (preset == 0) { // Minimal HUD
            StormClient.hudFpsEnabled = true;
            StormClient.hudCoordinatesEnabled = true;
            StormClient.hudDirectionEnabled = false;
            StormClient.hudHeldItemEnabled = false;
            StormClient.hudArmorEnabled = false;
            StormClient.hudActiveModulesEnabled = false;
            StormClient.hudMaceEnabled = false;
            StormClient.hudVitalsEnabled = false;
            StormClient.hudPingEnabled = false;
            StormClient.hudHeldDurabilityEnabled = false;
            StormClient.hudArmorDurabilityEnabled = false;
        } else if (preset == 1) { // Mace practice display
            StormClient.hudMaceEnabled = true;
            StormClient.hudVitalsEnabled = true;
            StormClient.hudCoordinatesEnabled = true;
            StormClient.hudHeldDurabilityEnabled = true;
        } else if (preset == 2) { // Exploration
            StormClient.hudCoordinatesEnabled = true;
            StormClient.hudDirectionEnabled = true;
            StormClient.hudFpsEnabled = true;
            StormClient.visiblePlayersEnabled = true;
            StormClient.visibleMobsEnabled = true;
            StormClient.chunkFinderEnabled = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int x = left(), y = top();
        g.fill(x - 2, y - 2, x + PANEL_W + 2, y + PANEL_H + 2, 0xFF3A8DFF);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, 0xF00B101A);
        g.fill(x, y, x + PANEL_W, y + 39, 0xFF151F30);
        g.fill(x, y + 39, x + PANEL_W, y + 41, 0xFF3A8DFF);
        g.fill(x + 137, y + 42, x + 139, y + PANEL_H - 12, 0xFF26364B);
        g.fill(x + 8, y + 47, x + 130, y + 295, 0x66151F30);
        g.text(font, "STORMCLIENT", x + 16, y + 14, 0xFFFFFFFF, true);
        g.text(font, "FABRIC  /  26.2", x + PANEL_W - 112, y + 14, 0xFF91A9C7, false);

        drawTab(g, "MODULES", Tab.MODULES, x + 12, y + 45, mouseX, mouseY);
        drawTab(g, "COMBOS", Tab.COMBOS, x + 12, y + 70, mouseX, mouseY);
        drawTab(g, "HUD", Tab.HUD, x + 12, y + 95, mouseX, mouseY);

        g.text(font, "CATEGORIES", x + 18, y + 132, 0xFF7289A6, true);
        int cy = y + 149;
        for (Category c : Category.values()) {
            boolean selected = category == c;
            int color = selected ? 0xFF72B8FF : 0xFFB5C2D4;
            if (selected) g.fill(x + 12, cy - 3, x + 126, cy + 17, 0x553A8DFF);
            g.text(font, (selected ? "› " : "  ") + c.name(), x + 19, cy + 2, color, selected);
            cy += 23;
        }

        int contentX = x + 153;
        g.text(font, tab == Tab.MODULES ? category.name() : tab.name(), contentX, y + 51, 0xFF72B8FF, true);
        g.text(font, "Search: " + (search.isEmpty() ? "type to filter..." : search + "_"),
                contentX, y + 69, 0xFFB5C2D4, false);
        g.fill(contentX, y + 85, x + PANEL_W - 14, y + 86, 0xFF26364B);

        if (tab == Tab.MODULES || tab == Tab.HUD) {
            List<ModuleEntry> list = tab == Tab.HUD ? hudModules() : modules();
            int startY = y + 96;
            int visible = 8;
            int maxScroll = Math.max(0, list.size() - visible);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            if (list.isEmpty()) {
                g.text(font, "No matching implemented modules.", contentX, startY + 8, 0xFFB5C2D4, false);
            }
            for (int i = scroll; i < Math.min(list.size(), scroll + visible); i++) {
                ModuleEntry m = list.get(i);
                int rowY = startY + (i - scroll) * ROW_H;
                g.fill(contentX, rowY, x + PANEL_W - 14, rowY + 27, 0x88303E52);
                g.text(font, m.name(), contentX + 8, rowY + 4, 0xFFF2F5FA, true);
                g.text(font, m.description(), contentX + 8, rowY + 16, 0xFF91A9C7, false);
                boolean on = m.enabled().getAsBoolean();
                g.fill(x + PANEL_W - 67, rowY + 6, x + PANEL_W - 23, rowY + 21,
                        on ? 0xFF267D55 : 0xFF465367);
                g.text(font, on ? "ON" : (m.name().equals("Zoom") ? "HOLD C" : "OFF"),
                        x + PANEL_W - 60, rowY + 10, 0xFFFFFFFF, true);
            }
            g.text(font, "Click a row to toggle  |  Scroll to see more",
                    contentX, y + 350, 0xFF7F91A8, false);
        } else {
            g.text(font, "QUICK PRESETS", contentX, y + 103, 0xFFB5C2D4, true);
            String[] names = {"Minimal HUD", "Mace Practice Display", "Exploration"};
            String[] descriptions = {
                    "A clean FPS + coordinates layout",
                    "Mace state, health and durability info",
                    "Coordinates, direction and visible entities"
            };
            for (int i = 0; i < names.length; i++) {
                int py = y + 126 + i * 58;
                g.fill(contentX, py, x + PANEL_W - 14, py + 48, 0x88303E52);
                g.text(font, names[i], contentX + 9, py + 8, 0xFFF2F5FA, true);
                g.text(font, descriptions[i], contentX + 9, py + 23, 0xFF91A9C7, false);
                g.text(font, "APPLY  →", x + PANEL_W - 76, py + 18, 0xFF72B8FF, true);
            }
            g.text(font, "Presets change only existing StormClient settings.",
                    contentX, y + 320, 0xFF7F91A8, false);
        }

        if (tab == Tab.MODULES && category == Category.UTILITY) {
            g.text(font, "Market Bot: " + MarketBot.getStatus(), contentX, y + 334, 0xFFFFD36A, false);
        }

        g.text(font, "RIGHT SHIFT: menu     C: zoom     ESC: close",
                x + 16, y + PANEL_H - 17, 0xFF7F91A8, false);
    }

    private void drawTab(GuiGraphicsExtractor g, String label, Tab target, int x, int y, int mx, int my) {
        boolean selected = tab == target;
        g.fill(x, y, x + 112, y + 21, selected ? 0x663A8DFF : 0x442B3B50);
        g.text(font, (selected ? "◆ " : "  ") + label, x + 7, y + 7,
                selected ? 0xFF72B8FF : 0xFFB5C2D4, selected);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int x = left(), y = top();
        if (mouseX >= x + 12 && mouseX <= x + 124) {
            if (mouseY >= y + 45 && mouseY <= y + 66) { tab = Tab.MODULES; scroll = 0; return true; }
            if (mouseY >= y + 70 && mouseY <= y + 91) { tab = Tab.COMBOS; scroll = 0; return true; }
            if (mouseY >= y + 95 && mouseY <= y + 116) { tab = Tab.HUD; scroll = 0; return true; }
            int index = (int)((mouseY - (y + 149)) / 23);
            if (mouseY >= y + 149 && index >= 0 && index < Category.values().length) {
                category = Category.values()[index]; tab = Tab.MODULES; scroll = 0; search = ""; return true;
            }
        }
        int contentX = x + 153;
        if (mouseX >= contentX && mouseX <= x + PANEL_W - 14) {
            if (tab == Tab.COMBOS && mouseY >= y + 126 && mouseY <= y + 300) {
                int preset = (int)((mouseY - (y + 126)) / 58);
                if (preset >= 0 && preset < 3) { applyPreset(preset); return true; }
            }
            if ((tab == Tab.MODULES || tab == Tab.HUD) && mouseY >= y + 96 && mouseY <= y + 96 + 8 * ROW_H) {
                int row = (int)((mouseY - (y + 96)) / ROW_H) + scroll;
                List<ModuleEntry> list = tab == Tab.HUD ? hudModules() : modules();
                if (row >= 0 && row < list.size() && !list.get(row).name().equals("Zoom")) {
                    list.get(row).toggle().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.MODULES || tab == Tab.HUD) {
            scroll = Math.max(0, scroll + (verticalAmount < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();
        if (tab != Tab.COMBOS && !Character.isISOControl(codepoint)) {
            search += new String(Character.toChars(codepoint));
            scroll = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.input();
        if (keyCode == 27) { onClose(); return true; }
        if (keyCode == 8 && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }
}
