package dev.stormclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StormClickGuiScreen extends Screen {
    private enum Page { MOVEMENT, VISUALS, UTILITY }
    private final Page page;

    public StormClickGuiScreen() {
        this(Page.MOVEMENT);
    }

    private StormClickGuiScreen(Page page) {
        super(Component.literal("StormClient"));
        this.page = page;
    }

    private void open(Page next) {
        Minecraft.getInstance().gui.setScreen(new StormClickGuiScreen(next));
    }

    @Override
    protected void init() {
        int panelWidth = 430;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(16, (this.height - 310) / 2);
        int tabY = top + 43;
        int tabWidth = 122;

        addRenderableWidget(tab("MOVEMENT", Page.MOVEMENT, left + 16, tabY, tabWidth));
        addRenderableWidget(tab("VISUALS", Page.VISUALS, left + 154, tabY, tabWidth));
        addRenderableWidget(tab("UTILITY", Page.UTILITY, left + 292, tabY, tabWidth));

        if (page == Page.MOVEMENT) {
            addRenderableWidget(Button.builder(airJumpLabel(), button -> {
                StormClient.airJumpEnabled = !StormClient.airJumpEnabled;
                button.setMessage(airJumpLabel());
            }).bounds(left + 20, top + 91, panelWidth - 40, 25).build());

            addRenderableWidget(Button.builder(sprintLabel(), button -> {
                StormClient.autoSprintEnabled = !StormClient.autoSprintEnabled;
                button.setMessage(sprintLabel());
            }).bounds(left + 20, top + 123, panelWidth - 40, 25).build());

            addRenderableWidget(Button.builder(Component.literal("Zoom: HOLD C"), button -> {})
                    .bounds(left + 20, top + 155, panelWidth - 40, 25).build());
        } else if (page == Page.VISUALS) {
            addRenderableWidget(Button.builder(fullbrightLabel(), button -> {
                StormClient.fullbrightEnabled = !StormClient.fullbrightEnabled;
                button.setMessage(fullbrightLabel());
            }).bounds(left + 20, top + 91, panelWidth - 40, 25).build());
            addRenderableWidget(Button.builder(Component.literal("Zoom: HOLD C"), button -> {})
                    .bounds(left + 20, top + 123, panelWidth - 40, 25).build());
        } else {
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 20, top + 267, panelWidth - 40, 22).build());
    }

    private Button tab(String label, Page target, int x, int y, int width) {
        String text = (page == target ? "• " : "") + label;
        return Button.builder(Component.literal(text), button -> open(target))
                .bounds(x, y, width, 24).build();
    }

    private static Component airJumpLabel() {
        return Component.literal("Air Jump: " + (StormClient.airJumpEnabled ? "ON" : "OFF"));
    }

    private static Component sprintLabel() {
        return Component.literal("Auto Sprint: " + (StormClient.autoSprintEnabled ? "ON" : "OFF"));
    }

    private static Component fullbrightLabel() {
        return Component.literal("Fullbright: " + (StormClient.fullbrightEnabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int panelWidth = 430;
        int panelHeight = 310;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(16, (this.height - panelHeight) / 2);

        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF168BFF);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101420);
        graphics.fill(left, top, left + panelWidth, top + 34, 0xFF101E38);
        graphics.text(this.font, "STORMCLIENT", left + 16, top + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "Right Shift: open/close menu", left + 265, top + 12, 0xFFB7C7DC, false);

        if (page == Page.MOVEMENT) {
            graphics.text(this.font, "MOVEMENT MODULES", left + 20, top + 78, 0xFF75BFFF, true);
            graphics.text(this.font, "Air Jump lets you jump again while airborne.", left + 20, top + 205, 0xFFB7C7DC, false);
            graphics.text(this.font, "Auto Sprint sprints while you move forward.", left + 20, top + 222, 0xFFB7C7DC, false);
        } else if (page == Page.VISUALS) {
            graphics.text(this.font, "VISUAL MODULES", left + 20, top + 78, 0xFF75BFFF, true);
            graphics.text(this.font, "Fullbright changes your local gamma setting.", left + 20, top + 205, 0xFFB7C7DC, false);
            graphics.text(this.font, "Zoom is hold-to-use on the C key.", left + 20, top + 222, 0xFFB7C7DC, false);
        } else {
            graphics.text(this.font, "WORLD & UTILITY", left + 20, top + 78, 0xFF75BFFF, true);
            graphics.text(this.font, "Base Finder: planned; not implemented yet.", left + 20, top + 112, 0xFFB7C7DC, false);
            graphics.text(this.font, "Waypoints: planned; not implemented yet.", left + 20, top + 133, 0xFFB7C7DC, false);
            graphics.text(this.font, "PearSMP dupe: compatibility NOT VERIFIED.", left + 20, top + 166, 0xFFFFC978, false);
            graphics.text(this.font, "There is no universal client-side multiplayer dupe.", left + 20, top + 187, 0xFFB7C7DC, false);
            graphics.text(this.font, "No fake activation button until a method is verified.", left + 20, top + 208, 0xFFB7C7DC, false);
        }
    }
}
