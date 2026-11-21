package dev.stormclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact category-sidebar ClickGUI for StormClient modules. */
public final class StormClickGuiScreen extends Screen {
    private enum Page { COMBAT, MOVEMENT, VISUALS, PLAYER, WORLD, HUD, UTILITY }
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
        int panelWidth = 540;
        int panelHeight = 390;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(8, (this.height - panelHeight) / 2);
        int navX = left + 10;
        int navY = top + 52;
        int contentX = left + 145;
        int contentWidth = panelWidth - 165;
        int y = top + 91;

        addRenderableWidget(tab("COMBAT", Page.COMBAT, navX, navY));
        addRenderableWidget(tab("MOVEMENT", Page.MOVEMENT, navX, navY + 34));
        addRenderableWidget(tab("VISUALS", Page.VISUALS, navX, navY + 68));
        addRenderableWidget(tab("PLAYER", Page.PLAYER, navX, navY + 102));
        addRenderableWidget(tab("WORLD", Page.WORLD, navX, navY + 136));
        addRenderableWidget(tab("HUD", Page.HUD, navX, navY + 170));
        addRenderableWidget(tab("UTILITY", Page.UTILITY, navX, navY + 204));

        int buttonWidth = contentWidth;
        int buttonLeft = contentX - 20;
        if (page == Page.MOVEMENT) {
            toggle(buttonLeft, y, buttonWidth, airJumpLabel(), () -> StormClient.airJumpEnabled = !StormClient.airJumpEnabled, 0);
            toggle(buttonLeft, y + 34, buttonWidth, sprintLabel(), () -> StormClient.autoSprintEnabled = !StormClient.autoSprintEnabled, 1);
            toggle(buttonLeft, y + 68, buttonWidth, elytraLabel(), () -> StormClient.elytraAssistEnabled = !StormClient.elytraAssistEnabled, 2);
        } else if (page == Page.VISUALS) {
            toggle(buttonLeft, y, buttonWidth, fullbrightLabel(), () -> StormClient.fullbrightEnabled = !StormClient.fullbrightEnabled, 3);
            addRenderableWidget(Button.builder(Component.literal("Zoom: HOLD C  [KEYBIND]"), button -> {})
                    .bounds(contentX, y + 34, buttonWidth, 27).build());
        } else if (page == Page.HUD) {
            toggle(buttonLeft, y, buttonWidth, fpsLabel(), () -> StormClient.hudFpsEnabled = !StormClient.hudFpsEnabled, 4);
            toggle(buttonLeft, y + 31, buttonWidth, coordinatesLabel(), () -> StormClient.hudCoordinatesEnabled = !StormClient.hudCoordinatesEnabled, 5);
            toggle(buttonLeft, y + 62, buttonWidth, directionLabel(), () -> StormClient.hudDirectionEnabled = !StormClient.hudDirectionEnabled, 6);
            toggle(buttonLeft, y + 93, buttonWidth, heldItemLabel(), () -> StormClient.hudHeldItemEnabled = !StormClient.hudHeldItemEnabled, 7);
            toggle(buttonLeft, y + 124, buttonWidth, armorLabel(), () -> StormClient.hudArmorEnabled = !StormClient.hudArmorEnabled, 8);
            toggle(buttonLeft, y + 155, buttonWidth, modulesLabel(), () -> StormClient.hudActiveModulesEnabled = !StormClient.hudActiveModulesEnabled, 9);
            toggle(buttonLeft, y + 186, buttonWidth, maceLabel(), () -> StormClient.hudMaceEnabled = !StormClient.hudMaceEnabled, 11);
        } else if (page == Page.UTILITY) {
            toggle(buttonLeft, y, buttonWidth, baseFinderLabel(), () -> StormClient.baseFinderEnabled = !StormClient.baseFinderEnabled, 10);
            addRenderableWidget(Button.builder(Component.literal("Scan nearby loaded chunks  (#base)"), button -> {
                Minecraft.getInstance().gui.setScreen(null);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.connection.sendChat("#base");
            }).bounds(contentX, y + 34, buttonWidth, 27).build());
            addRenderableWidget(Button.builder(Component.literal("List markers  (#base list)"), button -> {
                Minecraft.getInstance().gui.setScreen(null);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.connection.sendChat("#base list");
            }).bounds(contentX, y + 68, buttonWidth, 27).build());
            addRenderableWidget(Button.builder(Component.literal("Clear markers  (#base clear)"), button -> {
                Minecraft.getInstance().gui.setScreen(null);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.connection.sendChat("#base clear");
            }).bounds(contentX, y + 102, buttonWidth, 27).build());
        } else if (page == Page.PLAYER) {
            addRenderableWidget(Button.builder(Component.literal("Inventory + Bundle Inspector"), button ->
                    BundleInspector.inspect()).bounds(contentX, y, buttonWidth, 27).build());
        } else if (page == Page.COMBAT) {
            addRenderableWidget(Button.builder(Component.literal("Mace PvP HUD  →  configure in HUD"), button ->
                    open(Page.HUD)).bounds(contentX, y, buttonWidth, 27).build());
            addRenderableWidget(Button.builder(Component.literal("Combat modules are added individually"), button -> {})
                    .bounds(contentX, y + 34, buttonWidth, 27).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("World tools are being built in tested batches"), button -> {})
                    .bounds(contentX, y, buttonWidth, 27).build());
        }

        addRenderableWidget(Button.builder(Component.literal("CLOSE  [ESC]"), button -> onClose())
                .bounds(contentX, top + 350, buttonWidth, 24).build());
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
            case 11 -> maceLabel();
            default -> Component.literal("Unknown");
        };
    }

    private Button tab(String label, Page target, int x, int y) {
        String text = (page == target ? "◆ " : "   ") + label;
        return Button.builder(Component.literal(text), button -> open(target))
                .bounds(x, y, 122, 27).build();
    }

    private static Component state(String name, boolean enabled) {
        return Component.literal(name + "     " + (enabled ? "[ON]" : "[OFF]"));
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
    private static Component maceLabel() { return state("Mace PvP HUD (display only)", StormClient.hudMaceEnabled); }
    private static Component baseFinderLabel() { return state("Base Finder", StormClient.baseFinderEnabled); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int panelWidth = 540;
        int panelHeight = 390;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(8, (this.height - panelHeight) / 2);
        int contentX = left + 145;

        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF3A8DFF);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF00B101A);
        graphics.fill(left, top, left + panelWidth, top + 34, 0xFF151F30);
        graphics.fill(left, top + 34, left + panelWidth, top + 36, 0xFF3A8DFF);
        graphics.fill(left + 135, top + 36, left + 137, top + panelHeight - 10, 0xFF26364B);
        graphics.fill(left + 8, top + 43, left + 130, top + 300, 0x66151F30);
        graphics.text(this.font, "STORMCLIENT", left + 16, top + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "MODULE MENU  /  26.2", left + 380, top + 12, 0xFF91A9C7, false);

        String heading = switch (page) {
            case COMBAT -> "COMBAT";
            case MOVEMENT -> "MOVEMENT";
            case VISUALS -> "VISUALS";
            case PLAYER -> "PLAYER";
            case WORLD -> "WORLD";
            case HUD -> "HUD EDITOR";
            case UTILITY -> "UTILITY";
        };
        graphics.text(this.font, heading, contentX, top + 65, 0xFF72B8FF, true);
        graphics.text(this.font, "Each module has its own independent switch.", contentX, top + 78, 0xFF91A9C7, false);

        if (page == Page.COMBAT) {
            graphics.text(this.font, "PvP settings stay separate from movement and HUD.", contentX, top + 174, 0xFFB5C2D4, false);
            graphics.text(this.font, "No combat automation is enabled by this menu.", contentX, top + 191, 0xFFB5C2D4, false);
        } else if (page == Page.MOVEMENT) {
            graphics.text(this.font, "Client-side movement helpers; server rules apply.", contentX, top + 208, 0xFFB5C2D4, false);
        } else if (page == Page.VISUALS) {
            graphics.text(this.font, "Fullbright changes local gamma. Hold C to zoom.", contentX, top + 174, 0xFFB5C2D4, false);
        } else if (page == Page.PLAYER) {
            graphics.text(this.font, "Inventory inspection is read-only.", contentX, top + 174, 0xFFB5C2D4, false);
        } else if (page == Page.WORLD) {
            graphics.text(this.font, "More world tools will be added after testing.", contentX, top + 174, 0xFFB5C2D4, false);
        } else if (page == Page.HUD) {
            graphics.text(this.font, "Mace HUD is display-only; it never changes attacks or aim.", contentX, top + 318, 0xFFB5C2D4, false);
        } else if (page == Page.UTILITY) {
            graphics.text(this.font, "Base Finder only analyses chunks already loaded.", contentX, top + 174, 0xFFB5C2D4, false);
        }
        graphics.text(this.font, "RIGHT SHIFT: menu     C: zoom     ESC: close", left + 16, top + 373, 0xFF7F91A8, false);
    }
}
