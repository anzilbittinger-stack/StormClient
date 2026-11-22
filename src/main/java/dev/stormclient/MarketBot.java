package dev.stormclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;

/**
 * Market helper for servers using a Donut-style Auction House.
 *
 * It only operates while explicitly enabled and while an auction/confirmation
 * inventory is open. It never attempts to hide its actions or bypass server checks.
 */
public final class MarketBot {
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:\\$\\s*)?([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMB])?"
    );
    private static final double[] BUDGET_OPTIONS = {
            100_000, 500_000, 1_000_000, 5_000_000, 10_000_000,
            50_000_000, 100_000_000, 500_000_000, 1_000_000_000
    };
    private static int budgetIndex = 2;
    private static boolean reinvestEnabled = true;
    private static final double MAX_BUY_TO_MEDIAN_RATIO = 0.75;
    private static final double RESALE_TO_MEDIAN_RATIO = 0.95;
    private static final int REFRESH_TICKS = 200;

    private static int refreshTicks;
    private static int actionCooldown;
    private static boolean waitingForPurchase;
    private static boolean waitingForSale;
    private static String pendingItemId = "";
    private static double pendingSalePrice;
    private static String status = "Idle";

    private MarketBot() {}

    public static String getStatus() {
        return status;
    }

    public static double getBudget() {
        return BUDGET_OPTIONS[budgetIndex];
    }

    public static String getBudgetLabel() {
        double value = getBudget();
        if (value >= 1_000_000_000) return String.format(Locale.US, "%.0fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.US, "%.0fM", value / 1_000_000);
        return String.format(Locale.US, "%.0fK", value / 1_000);
    }

    public static void cycleBudget() {
        budgetIndex = (budgetIndex + 1) % BUDGET_OPTIONS.length;
    }

    public static boolean isReinvestEnabled() {
        return reinvestEnabled;
    }

    public static void toggleReinvest() {
        reinvestEnabled = !reinvestEnabled;
    }

    public static void tick(Minecraft client) {
        if (!StormClient.marketBotEnabled || client.player == null || client.level == null) {
            return;
        }
        if (actionCooldown > 0) actionCooldown--;
        if (refreshTicks > 0) refreshTicks--;

        if (waitingForSale && actionCooldown == 0) {
            waitingForSale = false;
            status = "Sale submitted";
            refreshTicks = 0;
            if (!reinvestEnabled) {
                StormClient.marketBotEnabled = false;
                status = "Flip complete — reinvest is OFF";
                return;
            }
        }

        if (client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
            if (isConfirmationTitle(title)) {
                handleConfirmation(client);
                return;
            }
            if (isAuctionTitle(title)) {
                if (waitingForPurchase && actionCooldown == 0) {
                    if (tryFindBoughtItem(client)) return;
                    status = "Waiting for purchased item...";
                    actionCooldown = 20;
                    return;
                }
                if (!waitingForPurchase && !waitingForSale && actionCooldown == 0) {
                    scanAuction(client);
                }
                return;
            }
        }

        if (waitingForPurchase && actionCooldown == 0) {
            if (tryFindBoughtItem(client)) return;
            status = "Waiting for purchased item...";
            actionCooldown = 20;
            return;
        }

        if (refreshTicks == 0 && actionCooldown == 0) {
            client.player.connection.sendCommand("ah");
            status = "Opening Auction House...";
            refreshTicks = REFRESH_TICKS;
            actionCooldown = 30;
        }
    }

    private static boolean isAuctionTitle(String title) {
        return title.contains("auction") || title.equals("ah") || title.contains("market");
    }

    private static boolean isConfirmationTitle(String title) {
        return title.contains("confirm") || title.contains("purchase")
                || title.contains("buy") || title.contains("sell");
    }

    private static void scanAuction(Minecraft client) {
        var menu = client.player.containerMenu;
        Map<String, List<Listing>> grouped = new HashMap<>();

        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            double price = readPrice(stack);
            if (!Double.isFinite(price) || price <= 0) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            grouped.computeIfAbsent(itemId, ignored -> new ArrayList<>())
                    .add(new Listing(i, itemId, price, stack.getCount()));
        }

        Listing best = null;
        double bestMedian = 0;
        for (List<Listing> listings : grouped.values()) {
            if (listings.size() < 3) continue;
            List<Double> prices = listings.stream().map(Listing::price).sorted().toList();
            double median = prices.get(prices.size() / 2);
            Listing cheapest = listings.stream().min(Comparator.comparingDouble(Listing::price)).orElse(null);
            if (cheapest == null || median <= 0) continue;
            double buyCost = cheapest.price() * 1.02;
            double resale = median * RESALE_TO_MEDIAN_RATIO;
            double profit = resale - buyCost;
            if (cheapest.price() > getBudget()) continue;
            if (cheapest.price() > median * MAX_BUY_TO_MEDIAN_RATIO) continue;
            if (profit <= 0 || profit / buyCost < 0.20) continue;
            if (best == null || profit > (bestMedian * RESALE_TO_MEDIAN_RATIO - best.price() * 1.02)) {
                best = cheapest;
                bestMedian = median;
            }
        }

        if (best == null) {
            status = "Scanning listings — no qualifying flip on this page";
            actionCooldown = 20;
            if (refreshTicks == 0) {
                client.player.connection.sendCommand("ah");
                refreshTicks = REFRESH_TICKS;
                actionCooldown = 30;
            }
            return;
        }

        pendingItemId = best.itemId();
        pendingSalePrice = Math.max(1, Math.floor(bestMedian * RESALE_TO_MEDIAN_RATIO));
        waitingForPurchase = true;
        status = "Buying " + pendingItemId + " for $" + format(best.price());
        clickSlot(client, best.slot());
        actionCooldown = 30;
    }

    private static void handleConfirmation(Minecraft client) {
        if (actionCooldown > 0) return;
        var menu = client.player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            String label = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String lore = loreText(stack).toLowerCase(Locale.ROOT);
            String text = label + " " + lore;
            if (text.contains("confirm") || text.contains("purchase")
                    || text.contains("buy now") || text.contains("accept")) {
                clickSlot(client, i);
                status = "Confirming market action...";
                actionCooldown = 30;
                return;
            }
        }
        status = "Confirmation menu detected; waiting for a recognizable confirm button";
        actionCooldown = 40;
    }

    private static boolean tryFindBoughtItem(Minecraft client) {
        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!id.equals(pendingItemId)) continue;
            if (i >= 9) {
                int menuSlot = i;
                clickSwapToHotbar(client, menuSlot, 8);
                status = "Moving purchased item to hotbar...";
                actionCooldown = 8;
                return true;
            }
            inventory.setSelectedSlot(i);
            client.player.connection.sendCommand("ah sell " + formatCommandPrice(pendingSalePrice));
            waitingForPurchase = false;
            waitingForSale = true;
            status = "Listing purchased item at $" + format(pendingSalePrice);
            actionCooldown = 40;
            return true;
        }
        return false;
    }

    private static void clickSwapToHotbar(Minecraft client, int slot, int hotbarButton) {
        if (client.gameMode == null || client.player == null) return;
        client.gameMode.handleContainerInput(
                client.player.containerMenu.containerId,
                slot,
                hotbarButton,
                ContainerInput.SWAP,
                client.player
        );
    }

    private static void clickSlot(Minecraft client, int slot) {
        if (client.gameMode == null || client.player == null) return;
        client.gameMode.handleContainerInput(
                client.player.containerMenu.containerId,
                slot,
                0,
                ContainerInput.PICKUP,
                client.player
        );
    }

    private static double readPrice(ItemStack stack) {
        String text = loreText(stack);
        Matcher matcher = MONEY.matcher(text);
        double largest = -1;
        while (matcher.find()) {
            try {
                double value = Double.parseDouble(matcher.group(1).replace(",", ""));
                String suffix = matcher.group(2);
                if (suffix != null) {
                    value *= switch (suffix.toUpperCase(Locale.ROOT)) {
                        case "K" -> 1_000.0;
                        case "M" -> 1_000_000.0;
                        case "B" -> 1_000_000_000.0;
                        default -> 1.0;
                    };
                }
                if (value > largest) largest = value;
            } catch (NumberFormatException ignored) {
                // Ignore malformed lore fragments.
            }
        }
        return largest;
    }

    private static String loreText(ItemStack stack) {
        StringBuilder text = new StringBuilder();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) text.append(line.getString()).append(' ');
        }
        return text.toString();
    }

    private static String format(double value) {
        return String.format(Locale.US, "%,.0f", value);
    }

    private static String formatCommandPrice(double value) {
        return String.format(Locale.US, "%.0f", value);
    }

    private record Listing(int slot, String itemId, double price, int count) {}
}
