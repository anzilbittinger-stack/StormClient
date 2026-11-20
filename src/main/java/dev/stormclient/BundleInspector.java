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
 *
 * The first scan records a local baseline. Later scans compare item totals
 * against that baseline and report changes; this is diagnostic evidence only,
 * not proof that the server duplicated or rolled back an item.
 */
public final class BundleInspector {
    private static Map<String, Integer> baseline;
    private static int baselineTotal;
    private static int baselineOccupied;
    private static int baselineMenuSlots;

    private BundleInspector() {}

    public static void inspect() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            tell("Join a world or server before inspecting your inventory.");
            return;
        }

        Map<String, ItemSummary> summaries = new LinkedHashMap<>();
        Map<String, Integer> currentTotals = new LinkedHashMap<>();
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
            currentTotals.merge(itemId, stack.getCount(), Integer::sum);
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

        int menuSlots = client.player.containerMenu.slots.size();
        tell("Snapshot: " + occupiedStacks + " occupied stacks, " + totalItems
                + " total items, " + bundleSlots.size() + " bundle stack(s), "
                + menuSlots + " open-menu slots.");

        if (baseline == null) {
            baseline = new LinkedHashMap<>(currentTotals);
            baselineTotal = totalItems;
            baselineOccupied = occupiedStacks;
            baselineMenuSlots = menuSlots;
            tell("BASELINE SAVED. Scan again after your test to compare inventory totals.");
        } else {
            reportChanges(currentTotals, totalItems, occupiedStacks, menuSlots);
            baseline = new LinkedHashMap<>(currentTotals);
            baselineTotal = totalItems;
            baselineOccupied = occupiedStacks;
            baselineMenuSlots = menuSlots;
            tell("Baseline refreshed. The next scan compares against this snapshot.");
        }

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

        tell("Read-only scan: no packets sent, no slot edits, and no forced disconnects.");
    }

    private static void reportChanges(Map<String, Integer> current, int totalItems,
                                      int occupiedStacks, int menuSlots) {
        tell("CHANGE SINCE PREVIOUS SCAN:");
        tell("Total item count: " + signed(totalItems - baselineTotal)
                + "; occupied stacks: " + signed(occupiedStacks - baselineOccupied)
                + "; menu slots: " + signed(menuSlots - baselineMenuSlots) + ".");

        Map<String, Integer> allIds = new LinkedHashMap<>(baseline);
        current.forEach(allIds::putIfAbsent);
        int reported = 0;
        for (String id : allIds.keySet()) {
            int before = baseline.getOrDefault(id, 0);
            int after = current.getOrDefault(id, 0);
            if (before == after) continue;
            tell(id + ": " + before + " -> " + after + " (" + signed(after - before) + ").");
            reported++;
            if (reported >= 10) {
                tell("Further item-type changes omitted.");
                break;
            }
        }
        if (reported == 0) {
            tell("No item-type total changes detected.");
        }
        tell("A count change alone cannot distinguish normal use, server rollback, or duplication.");
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
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
