package dev.stormclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Madxray-inspired dark, category-based ClickGUI for permitted client utilities. */
public final class StormClickGuiScreen extends Screen {
    private enum Page { MOVEMENT, VISUALS, HUD, UTILITY, DUPE_LAB }
    private final Page page;

    public StormClickGuiScreen() { this(Page.MOVEMENT); }

    private StormClickGuiScreen(Page page) {
        super(Component.literal("StormClient"));
        this.page = page;
    }

    private void open(Page next) {
        Minecraft.getInstance().gui.setScreen(new StormClickGuiScreen(next));
    }

    @Override
    protected void init() {
        int panelWidth = 470;
        int panelHeight = 330;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(12, (this.height - panelHeight) / 2);
        int tabY = top + 42;

        addRenderableWidget(tab("MOVE", Page.MOVEMENT, left + 12, tabY, 82));
        addRenderableWidget(tab("VISUALS", Page.VISUALS, left + 97, tabY, 82));
        addRenderableWidget(tab("HUD", Page.HUD, left + 182, tabY, 82));
        addRenderableWidget(tab("UTILITY", Page.UTILITY, left + 267, tabY, 82));
        addRenderableWidget(tab("DUPE LAB", Page.DUPE_LAB, left + 352, tabY, 106));

        int y = top + 91;
        int buttonWidth = panelWidth - 40;
        if (page == Page.MOVEMENT) {
            toggle(left, y, buttonWidth, airJumpLabel(), () -> StormClient.airJumpEnabled = !StormClient.airJumpEnabled, 0);
            toggle(left, y + 34, buttonWidth, sprintLabel(), () -> StormClient.autoSprintEnabled = !StormClient.autoSprintEnabled, 1);
            toggle(left, y + 68, buttonWidth, elytraLabel(), () -> StormClient.elytraAssistEnabled = !StormClient.elytraAssistEnabled, 2);
        } else if (page == Page.VISUALS) {
            toggle(left, y, buttonWidth, fullbrightLabel(), () -> StormClient.fullbrightEnabled = !StormClient.fullbrightEnabled, 3);
            addRenderableWidget(Button.builder(Component.literal("Zoom: HOLD C"), button -> {})
                    .bounds(left + 20, y + 34, buttonWidth, 26).build());
        } else if (page == Page.HUD) {
            toggle(left, y, buttonWidth, fpsLabel(), () -> StormClient.hudFpsEnabled = !StormClient.hudFpsEnabled, 4);
            toggle(left, y + 34, buttonWidth, coordinatesLabel(), () -> StormClient.hudCoordinatesEnabled = !StormClient.hudCoordinatesEnabled, 5);
            toggle(left, y + 68, buttonWidth, directionLabel(), () -> StormClient.hudDirectionEnabled = !StormClient.hudDirectionEnabled, 6);
            toggle(left, y + 102, buttonWidth, heldItemLabel(), () -> StormClient.hudHeldItemEnabled = !StormClient.hudHeldItemEnabled, 7);
            toggle(left, y + 136, buttonWidth, armorLabel(), () -> StormClient.hudArmorEnabled = !StormClient.hudArmorEnabled, 8);
            toggle(left, y + 170, buttonWidth, modulesLabel(), () -> StormClient.hudActiveModulesEnabled = !StormClient.hudActiveModulesEnabled, 9);
        } else if (page == Page.UTILITY) {
            toggle(left, y, buttonWidth, baseFinderLabel(), () -> StormClient.baseFinderEnabled = !StormClient.baseFinderEnabled, 10);
            addRenderableWidget(Button.builder(Component.literal("Scan nearby loaded chunks: type #base"), button -> {
                Minecraft.getInstance().gui.setScreen(null);
                Minecraft.getInstance().player.connection.sendChat("#base");
            }).bounds(left + 20, y + 34, buttonWidth, 26).build());
            addRenderableWidget(Button.builder(Component.literal("List / clear markers: #base list / #base clear"), button -> {})
                    .bounds(left + 20, y + 68, buttonWidth, 26).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Inspect Inventory + Bundles"), button ->
                    BundleInspector.inspect()).bounds(left + 20, y + 18, buttonWidth, 28).build());
        }

        addRenderableWidget(Button.builder(Component.literal("CLOSE  [ESC]"), button -> onClose())
                .bounds(left + 20, top + 291, buttonWidth, 23).build());
    }

    private void toggle(int left, int y, int width, Component label, Runnable change, int id) {
        addRenderableWidget(Button.builder(label, button -> {
            change.run();
            button.setMessage(labelFor(id));
        }).bounds(left + 20, y, width, 27).build());
    }

    private static Component labelFor(int id) {
        return switch (id) {
            case 0 -> airJumpLabel();
            case 1 -> sprintLabel();
            case 2 -> elytraLabel();
            case 3 -> fullbrightLabel();
            case 4 -> fpsLabel();
            case 5 -> coordinatesLabel();
            case 6 -> directionLabel();
            case 7 -> heldItemLabel();
            case 8 -> armorLabel();
            case 9 -> modulesLabel();
            case 10 -> baseFinderLabel();
            default -> Component.literal("Unknown");
        };
    }

    private Button tab(String label, Page target, int x, int y, int width) {
        String text = (page == target ? "◆ " : "") + label;
        return Button.builder(Component.literal(text), button -> open(target))
                .bounds(x, y, width, 25).build();
    }

    private static Component state(String name, boolean enabled) {
        return Component.literal(name + "   " + (enabled ? "[ON]" : "[OFF]"));
    }
    private static Component airJumpLabel() { return state("Air Jump", StormClient.airJumpEnabled); }
    private static Component sprintLabel() { return state("Auto Sprint", StormClient.autoSprintEnabled); }
    private static Component elytraLabel() { return state("Elytra Assist", StormClient.elytraAssistEnabled); }
    private static Component fullbrightLabel() { return state("Fullbright", StormClient.fullbrightEnabled); }
    private static Component fpsLabel() { return state("FPS Counter", StormClient.hudFpsEnabled); }
    private static Component coordinatesLabel() { return state("Coordinates HUD", StormClient.hudCoordinatesEnabled); }
    private static Component directionLabel() { return state("Direction HUD", StormClient.hudDirectionEnabled); }
    private static Component heldItemLabel() { return state("Held Item HUD", StormClient.hudHeldItemEnabled); }
    private static Component armorLabel() { return state("Armor HUD", StormClient.hudArmorEnabled); }
    private static Component modulesLabel() { return state("Active Modules HUD", StormClient.hudActiveModulesEnabled); }
    private static Component baseFinderLabel() { return state("Base Finder", StormClient.baseFinderEnabled); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int panelWidth = 470;
        int panelHeight = 330;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(12, (this.height - panelHeight) / 2);

        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF3A8DFF);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF00B101A);
        graphics.fill(left, top, left + panelWidth, top + 32, 0xFF151F30);
        graphics.fill(left, top + 32, left + panelWidth, top + 34, 0xFF3A8DFF);
        graphics.text(this.font, "STORMCLIENT", left + 16, top + 11, 0xFFFFFFFF, true);
        graphics.text(this.font, "CLIENT UTILITIES  /  26.2", left + 322, top + 11, 0xFF91A9C7, false);

        String heading = switch (page) {
            case MOVEMENT -> "MOVEMENT";
            case VISUALS -> "VISUALS";
            case HUD -> "HUD EDITOR";
            case UTILITY -> "UTILITY";
            case DUPE_LAB -> "INVENTORY DIAGNOSTICS";
        };
        graphics.text(this.font, heading, left + 20, top + 77, 0xFF72B8FF, true);

        if (page == Page.MOVEMENT) {
            graphics.text(this.font, "Client-side movement helpers. Server rules still apply.", left + 20, top + 208, 0xFFB5C2D4, false);
            graphics.text(this.font, "Elytra Assist adjusts pitch gently while gliding.", left + 20, top + 225, 0xFFB5C2D4, false);
        } else if (page == Page.VISUALS) {
            graphics.text(this.font, "Fullbright changes your local gamma setting.", left + 20, top + 174, 0xFFB5C2D4, false);
            graphics.text(this.font, "Hold C to zoom; release C to restore your FOV.", left + 20, top + 191, 0xFFB5C2D4, false);
        } else if (page == Page.HUD) {
            graphics.text(this.font, "Choose which information appears in the top-left HUD.", left + 20, top + 278, 0xFFB5C2D4, false);
        } else if (page == Page.UTILITY) {
            graphics.text(this.font, "Base Finder is heuristic and checks loaded chunks only.", left + 20, top + 178, 0xFFB5C2D4, false);
            graphics.text(this.font, "It does not reveal unloaded areas or hidden players.", left + 20, top + 195, 0xFFB5C2D4, false);
        } else {
            graphics.text(this.font, "Read-only inventory and bundle diagnostics.", left + 20, top + 156, 0xFFB5C2D4, false);
            graphics.text(this.font, "No packets, slot edits, duplication, or disconnect tricks.", left + 20, top + 175, 0xFFFFC978, false);
        }
        graphics.text(this.font, "Right Shift: menu  |  C: zoom", left + 20, top + 315, 0xFF7F91A8, false);
    }
}
