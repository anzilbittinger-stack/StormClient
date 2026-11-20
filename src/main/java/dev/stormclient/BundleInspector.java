package dev.stormclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only inventory diagnostics. This deliberately sends no packets and
 * never changes slots, item stacks, containers, or connection state.
 */
public final class BundleInspector {
    private BundleInspector() {}

    public static void inspect() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            tell("Dupe Lab: join a world or server before inspecting your inventory.");
            return;
        }

        Map<String, ItemSummary> summaries = new LinkedHashMap<>();
        List<Integer> bundleSlots = new ArrayList<>();
        int occupiedStacks = 0;
        int totalItems = 0;

        var inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            occupiedStacks++;
            totalItems += stack.getCount();

            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem()).toString();
            String displayName = stack.getHoverName().getString();
            summaries.compute(itemId, (key, current) -> {
                if (current == null) return new ItemSummary(itemId, displayName, 1, stack.getCount());
                current.stacks++;
                current.total += stack.getCount();
                return current;
            });

            if (itemId.endsWith(":bundle") || itemId.endsWith("_bundle")) {
                bundleSlots.add(slot);
            }
        }

        tell("Inventory snapshot: " + occupiedStacks + " occupied stacks, " + totalItems
                + " total items, " + bundleSlots.size() + " bundle stack(s).");
        tell("Open menu slot count: " + client.player.containerMenu.slots.size() + ".");
        if (bundleSlots.isEmpty()) {
            tell("No bundle item found in your player inventory.");
        } else {
            tell("Bundle item slot(s): " + bundleSlots + ".");
        }

        List<ItemSummary> repeated = summaries.values().stream()
                .filter(summary -> summary.stacks > 1)
                .sorted(Comparator.comparingInt((ItemSummary summary) -> summary.total).reversed())
                .limit(6)
                .toList();

        if (repeated.isEmpty()) {
            tell("No repeated item types found across inventory slots.");
        } else {
            tell("Repeated item types (normal stacks, NOT evidence of a dupe):");
            for (ItemSummary summary : repeated) {
                tell(summary.name + " [" + summary.id + "]: " + summary.stacks
                        + " stacks / " + summary.total + " items.");
            }
        }

        tell("Read-only scan only: it cannot verify server-side rollback or create items.");
    }

    private static void tell(String message) {
        Minecraft.getInstance().showDebugChat(Component.literal("[StormClient | Dupe Lab] " + message));
    }

    private static final class ItemSummary {
        final String id;
        final String name;
        int stacks;
        int total;

        ItemSummary(String id, String name, int stacks, int total) {
            this.id = id;
            this.name = name;
            this.stacks = stacks;
            this.total = total;
        }
    }
}
