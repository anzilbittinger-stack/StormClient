package dev.stormclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StormClient implements ClientModInitializer {
    public static final String MOD_ID = "stormclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static boolean airJumpEnabled = true;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
    );
    private static KeyMapping openGuiKey;
    private static boolean jumpWasDown;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.stormclient.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                client.gui.setScreen(new StormClickGuiScreen());
            }

            if (client.player == null || client.level == null) {
                jumpWasDown = false;
                return;
            }

            boolean jumpDown = client.options.keyJump.isDown();
            if (airJumpEnabled && jumpDown && !jumpWasDown
                    && !client.player.onGround()
                    && !client.player.isPassenger()
                    && !client.player.isFallFlying()) {
                client.player.jumpFromGround();
            }
            jumpWasDown = jumpDown;
        });

        LOGGER.info("StormClient initialised. Right Shift opens the ClickGUI.");
    }
}
