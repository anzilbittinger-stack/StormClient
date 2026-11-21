package dev.stormclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StormClient implements ClientModInitializer {
    public static final String MOD_ID = "stormclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean airJumpEnabled = true;
    public static boolean fullbrightEnabled = false;
    public static boolean autoSprintEnabled = false;
    public static boolean baseFinderEnabled = true;
    public static boolean elytraAssistEnabled = false;\n    public static boolean hudFpsEnabled = true;\n    public static boolean hudCoordinatesEnabled = true;\n    public static boolean hudDirectionEnabled = false;\n    public static boolean hudHeldItemEnabled = false;\n    public static boolean hudArmorEnabled = false;\n    public static boolean hudActiveModulesEnabled = false;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );
    private static KeyMapping openGuiKey;
    private static KeyMapping zoomKey;
    private static boolean jumpWasDown;
    private static boolean zooming;
    private static int originalFov = -1;
    private static double originalGamma = -1.0;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stormclient.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY
        ));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stormclient.zoom",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY
        ));

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.equalsIgnoreCase("#base") || message.equalsIgnoreCase("#base list")
                    || message.equalsIgnoreCase("#base clear")) {
                Minecraft.getInstance().execute(() -> BaseFinder.handleChatCommand(message));
                return false;
            }
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                client.gui.setScreen(new StormClickGuiScreen());
            }

            if (client.player == null || client.level == null) {
                jumpWasDown = false;
                if (zooming) {
                    client.options.fov().set(originalFov);
                    zooming = false;
                    originalFov = -1;
                }
                return;
            }

            if (autoSprintEnabled && client.player.input.hasForwardImpulse()) {
                client.player.setSprinting(true);
            }

            boolean jumpDown = client.options.keyJump.isDown();
            if (airJumpEnabled && jumpDown && !jumpWasDown
                    && !client.player.onGround()
                    && !client.player.isPassenger()
                    && !client.player.isFallFlying()) {
                client.player.jumpFromGround();
            }
            jumpWasDown = jumpDown;

            if (elytraAssistEnabled && client.player.isFallFlying()
                    && client.player.input.hasForwardImpulse()
                    && !client.options.keyShift.isDown()) {
                float pitch = client.player.getXRot();
                float targetPitch = -10.0f;
                float difference = targetPitch - pitch;
                float adjustment = Math.max(-0.6f, Math.min(0.6f, difference));
                client.player.setXRot(pitch + adjustment);
            }

            if (fullbrightEnabled) {
                if (originalGamma < 0.0) {
                    originalGamma = client.options.gamma().get();
                }
                client.options.gamma().set(16.0);
            } else if (originalGamma >= 0.0) {
                client.options.gamma().set(originalGamma);
                originalGamma = -1.0;
            }

            if (zoomKey.isDown()) {
                if (!zooming) {
                    originalFov = client.options.fov().get();
                    zooming = true;
                }
                client.options.fov().set(30);
            } else if (zooming) {
                client.options.fov().set(originalFov);
                zooming = false;
                originalFov = -1;
            }
        });

        StormHud.register();\n        LOGGER.info("StormClient initialised. Right Shift opens ClickGUI; C zooms.");
    }
}
