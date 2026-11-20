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
        int panelWidth = 300;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(35, (this.height - 230) / 2);

        this.addRenderableWidget(Button.builder(airJumpLabel(), button -> {
            StormClient.airJumpEnabled = !StormClient.airJumpEnabled;
            button.setMessage(airJumpLabel());
        }).bounds(left + 25, top + 75, panelWidth - 50, 24).build());

        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
                .bounds(left + 25, top + 185, panelWidth - 50, 20).build());
    }

    private static Component airJumpLabel() {
        return Component.literal("Air Jump: " + (StormClient.airJumpEnabled ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int panelWidth = 300;
        int panelHeight = 230;
        int left = (this.width - panelWidth) / 2;
        int top = Math.max(35, (this.height - panelHeight) / 2);

        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF168BFF);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0101420);
        graphics.fill(left, top, left + panelWidth, top + 34, 0xFF101E38);
        graphics.text(this.font, "STORMCLIENT", left + 14, top + 12, 0xFFFFFFFF, true);
        graphics.text(this.font, "MOVEMENT", left + 25, top + 54, 0xFF75BFFF, true);
        graphics.text(this.font, "More modules are coming in future builds.", left + 25, top + 145, 0xFFB7C7DC, false);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }
}
