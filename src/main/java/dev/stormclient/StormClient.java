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
    public static boolean elytraAssistEnabled = false;
    public static boolean hudFpsEnabled = true;
    public static boolean hudCoordinatesEnabled = true;
    public static boolean hudDirectionEnabled = false;
    public static boolean hudHeldItemEnabled = false;
    public static boolean hudArmorEnabled = false;
    public static boolean hudActiveModulesEnabled = false;
    public static boolean hudMaceEnabled = false;
    public static boolean hudVitalsEnabled = false;
    public static boolean hudPingEnabled = false;
    public static boolean hudWorldTimeEnabled = false;
    public static boolean hudHeldDurabilityEnabled = false;
    public static boolean hudArmorDurabilityEnabled = false;
    public static boolean visiblePlayersEnabled = false;
    public static boolean visibleMobsEnabled = false;
    public static boolean totemAlertEnabled = false;
    public static boolean autoTotemEnabled = false;
    public static boolean autoArmorEnabled = false;
    public static boolean autoRespawnEnabled = false;
    private static int autoTotemCooldown = 0;
    private static int autoArmorCooldown = 0;

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
                    || message.equalsIgnoreCase("#base clear") || message.equalsIgnoreCase("#base tp")
                    || message.toLowerCase(java.util.Locale.ROOT).startsWith("#base tp ")) {
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

            if (autoTotemCooldown > 0) autoTotemCooldown--;
            if (autoArmorCooldown > 0) autoArmorCooldown--;
            if (autoArmorEnabled && autoArmorCooldown == 0
                    && client.player.containerMenu == client.player.inventoryMenu
                    && client.gameMode != null) {
                net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.FEET
                };
                for (int i = 0; i < armorSlots.length; i++) {
                    if (!client.player.getItemBySlot(armorSlots[i]).isEmpty()) continue;
                    for (int hotbar = 0; hotbar < 9; hotbar++) {
                        var candidate = client.player.getInventory().getItem(hotbar);
                        String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(candidate.getItem()).getPath();
                        boolean matchesSlot = switch (armorSlots[i]) {
                            case HEAD -> itemName.endsWith("_helmet") || itemName.equals("turtle_helmet");
                            case CHEST -> itemName.endsWith("_chestplate") || itemName.equals("elytra");
                            case LEGS -> itemName.endsWith("_leggings");
                            case FEET -> itemName.endsWith("_boots");
                            default -> false;
                        };
                        if (matchesSlot) {
                            client.gameMode.handleContainerInput(
                                    client.player.inventoryMenu.containerId,
                                    5 + i,
                                    hotbar,
                                    net.minecraft.world.inventory.ContainerInput.SWAP,
                                    client.player
                            );
                            autoArmorCooldown = 8;
                            break;
                        }
                    }
                    if (autoArmorCooldown > 0) break;
                }
            }
            if (autoRespawnEnabled && client.player.isDeadOrDying()) {
                client.player.respawn();
            }
            if (autoTotemEnabled && autoTotemCooldown == 0
                    && client.player.containerMenu == client.player.inventoryMenu
                    && client.gameMode != null
                    && client.player.getOffhandItem().isEmpty()) {
                for (int inventorySlot = 0; inventorySlot < client.player.getInventory().getContainerSize(); inventorySlot++) {
                    var stack = client.player.getInventory().getItem(inventorySlot);
                    if (stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                        int menuSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
                        client.gameMode.handleContainerInput(
                                client.player.inventoryMenu.containerId,
                                menuSlot,
                                40,
                                net.minecraft.world.inventory.ContainerInput.SWAP,
                                client.player
                        );
                        autoTotemCooldown = 10;
                        break;
                    }
                }
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

        StormHud.register();
        LOGGER.info("StormClient initialised. Right Shift opens ClickGUI; C zooms.");
    }
}
