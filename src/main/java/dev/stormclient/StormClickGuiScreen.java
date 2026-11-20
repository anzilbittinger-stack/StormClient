package dev.stormclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StormClickGuiScreen extends Screen {
    public StormClickGuiScreen() {
        super(Component.literal("StormClient"));
    }

    @Override
    protected void init() {
        int panelWidth = 340;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(20, (this.height - 290) / 2);

        this.addRenderableWidget(Button.builder(airJumpLabel(), button -> {
            StormClient.airJumpEnabled = !StormClient.airJumpEnabled;
            button.setMessage(airJumpLabel());
        }).bounds(left + 22, top + 66, panelWidth - 44, 22).build());

        this.addRenderableWidget(Button.builder(fullbrightLabel(), button -> {
            StormClient.fullbrightEnabled = !StormClient.fullbrightEnabled;
            button.setMessage(fullbrightLabel());
        }).bounds(left + 22, top + 94, panelWidth - 44, 22).build());

        this.addRenderableWidget(Button.builder(Component.literal("Zoom: HOLD C"), button -> {})
                .bounds(left + 22, top + 122, panelWidth - 44, 22).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(left + 22, top + 246, panelWidth - 44, 20).build());
    }

    private static Component airJumpLabel() {
        return Component.literal("Air Jump: " + (StormClient.airJumpEnabled ? "ON" : "OFF"));
    }

    private static Component fullbrightLabel() {
        return Component.literal("Fullbright: " + (StormClient.fullbrightEnabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int panelWidth = 340;
        int panelHeight = 290;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(20, (this.height - panelHeight) / 2);

        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF168BFF);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101420);
        graphics.fill(left, top, left + panelWidth, top + 34, 0xFF101E38);
        graphics.text(this.font, "STORMCLIENT", left + 14, top + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "MOVEMENT", left + 22, top + 51, 0xFF75BFFF, true);
        graphics.text(this.font, "VISUALS", left + 22, top + 153, 0xFF75BFFF, true);
        graphics.text(this.font, "Zoom: hold C (release to restore FOV)", left + 22, top + 177, 0xFFB7C7DC, false);
        graphics.text(this.font, "Mace damage boost needs a matching server mod.", left + 22, top + 204, 0xFFFFC978, false);
        graphics.text(this.font, "Right Shift opens this menu.", left + 22, top + 224, 0xFFB7C7DC, false);
    }
}
