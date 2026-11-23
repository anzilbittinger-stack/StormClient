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
 * Conservative helper for Pear/Donut-style auction and order menus.
 * It uses visible menu data and ordinary inventory clicks/commands only.
 */
public final class MarketBot {
    private static final Pattern MONEY = Pattern.compile(
            "(?i)\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMB])?"
    );
    private static final Pattern PAGE = Pattern.compile("(?i)page\\s+(\\d+)");
    private static final double[] BUDGET_OPTIONS = {
            100_000, 500_000, 1_000_000, 5_000_000, 10_000_000,
            50_000_000, 100_000_000, 500_000_000, 1_000_000_000
    };
    private static int budgetIndex = 2;
    private static boolean reinvestEnabled = true;
    private static final double MAX_BUY_TO_MEDIAN_RATIO = 0.75;
    private static final double RESALE_TO_MEDIAN_RATIO = 0.95;
    private static final int MAX_PAGES = 9;

    private enum Phase { IDLE, AUCTION_SCAN, RETURN_TO_BUY_PAGE, WAIT_BUY, ORDERS_SCAN, WAIT_DELIVERY, WAIT_SALE }
    private static Phase phase = Phase.IDLE;
    private static int actionCooldown;
    private static int currentPage = 1;
    private static int targetBuyPage = 1;
    private static int orderPage = 1;
    private static boolean scanningOrders;
    private static String pendingItemId = "";
    private static double pendingSalePrice;
    private static int pendingBuySlot = -1;
    private static String status = "Idle";
    private static final Map<String, List<Listing>> auctionListings = new HashMap<>();
    private static final Map<String, Integer> deliveredOrders = new HashMap<>();

    private MarketBot() {}

    public static String getStatus() { return status; }
    public static double getBudget() { return BUDGET_OPTIONS[budgetIndex]; }

    public static String getBudgetLabel() {
        double value = getBudget();
        if (value >= 1_000_000_000) return String.format(Locale.US, "%.0fB", value / 1_000_000_000);
        if (value >= 1_000_000) return String.format(Locale.US, "%.0fM", value / 1_000_000);
        return String.format(Locale.US, "%.0fK", value / 1_000);
    }

    public static void cycleBudget() { budgetIndex = (budgetIndex + 1) % BUDGET_OPTIONS.length; }
    public static boolean isReinvestEnabled() { return reinvestEnabled; }
    public static void toggleReinvest() { reinvestEnabled = !reinvestEnabled; }

    public static void tick(Minecraft client) {
        if (!StormClient.marketBotEnabled || client.player == null || client.level == null) {
            phase = Phase.IDLE;
            return;
        }
        if (actionCooldown > 0) actionCooldown--;
        if (actionCooldown > 0) return;

        if (client.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
            if (isConfirmationTitle(title)) {
                handleConfirmation(client);
                return;
            }
            if (isAuctionTitle(title)) {
                if (phase == Phase.WAIT_BUY) {
                    if (tryFindBoughtItem(client)) return;
                    status = "Waiting for purchased item to enter inventory";
                    actionCooldown = 20;
                    return;
                }
                if (phase == Phase.AUCTION_SCAN) {
                    scanAuctionPage(client);
                    return;
                }
                if (phase == Phase.RETURN_TO_BUY_PAGE) {
                    if (currentPage > targetBuyPage) {
                        int previous = findNavigationSlot(client, false);
                        if (previous < 0) {
                            status = "Can't identify previous-page button; stopped before buying";
                            phase = Phase.IDLE;
                            return;
                        }
                        clickSlot(client, previous);
                        currentPage--;
                        status = "Returning to deal page " + targetBuyPage + "/" + MAX_PAGES;
                        actionCooldown = 20;
                        return;
                    }
                    if (pendingBuySlot >= 0) {
                        phase = Phase.WAIT_BUY;
                        status = "Buying " + pendingItemId + " for $" + format(findPendingPrice());
                        clickSlot(client, pendingBuySlot);
                        actionCooldown = 25;
                        return;
                    }
                }
                if (phase == Phase.IDLE) startAuctionScan(client);
                return;
            }
            if (isOrdersTitle(title)) {
                if (phase == Phase.WAIT_DELIVERY) {
                    status = "Checking order delivery result";
                    phase = Phase.ORDERS_SCAN;
                    actionCooldown = 25;
                    return;
                }
                if (phase == Phase.ORDERS_SCAN) {
                    scanOrdersPage(client);
                    return;
                }
                return;
            }
        }

        if (phase == Phase.WAIT_BUY) {
            if (tryFindBoughtItem(client)) return;
            status = "Waiting for purchased item to enter inventory";
            actionCooldown = 20;
            return;
        }
        if (phase == Phase.WAIT_SALE) {
            phase = Phase.IDLE;
            status = "Sale command sent; checking next opportunity";
            if (!reinvestEnabled) {
                StormClient.marketBotEnabled = false;
                status = "Flip submitted — reinvest is OFF";
                return;
            }
        }
        if (phase == Phase.WAIT_DELIVERY) {
            phase = Phase.ORDERS_SCAN;
            actionCooldown = 20;
            return;
        }
        if (phase == Phase.IDLE) {
            startAuctionScan(client);
        }
    }

    private static void startAuctionScan(Minecraft client) {
        auctionListings.clear();
        currentPage = 1;
        targetBuyPage = 1;
        pendingBuySlot = -1;
        phase = Phase.AUCTION_SCAN;
        client.player.connection.sendCommand("ah");
        status = "Opening Auction House — page 1/" + MAX_PAGES;
        actionCooldown = 25;
    }

    private static void scanAuctionPage(Minecraft client) {
        var menu = client.player.containerMenu;
        int added = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty() || isMenuControl(stack)) continue;
            double price = readPrice(stack);
            if (price <= 0 || !Double.isFinite(price)) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            auctionListings.computeIfAbsent(id, ignored -> new ArrayList<>())
                    .add(new Listing(currentPage, i, id, price, stack.getCount()));
            added++;
        }

        int next = findNavigationSlot(client, true);
        if (next >= 0 && currentPage < MAX_PAGES) {
            clickSlot(client, next);
            status = "Auction scan: page " + currentPage + "/" + MAX_PAGES
                    + " (" + added + " priced listings)";
            currentPage++;
            actionCooldown = 22;
            return;
        }
        finishAuctionScan(client);
    }

    private static void finishAuctionScan(Minecraft client) {
        Listing best = null;
        double bestMedian = 0;
        for (List<Listing> listings : auctionListings.values()) {
            if (listings.size() < 3) continue;
            List<Double> prices = listings.stream().map(Listing::price).sorted().toList();
            double median = prices.get(prices.size() / 2);
            Listing cheapest = listings.stream().min(Comparator.comparingDouble(Listing::price)).orElse(null);
            if (cheapest == null || median <= 0 || cheapest.price() > getBudget()) continue;
            double cost = cheapest.price() * 1.02;
            double resale = median * RESALE_TO_MEDIAN_RATIO;
            double profit = resale - cost;
            if (cheapest.price() > median * MAX_BUY_TO_MEDIAN_RATIO
                    || profit <= 0 || profit / cost < 0.20) continue;
            if (best == null || profit > bestMedian * RESALE_TO_MEDIAN_RATIO - best.price() * 1.02) {
                best = cheapest;
                bestMedian = median;
            }
        }

        if (best != null) {
            pendingItemId = best.itemId();
            pendingSalePrice = Math.max(1, Math.floor(bestMedian * RESALE_TO_MEDIAN_RATIO));
            pendingBuySlot = best.slot();
            targetBuyPage = best.page();
            if (currentPage > targetBuyPage) {
                phase = Phase.RETURN_TO_BUY_PAGE;
                status = "Found deal; returning to page " + targetBuyPage;
                actionCooldown = 5;
                return;
            }
            phase = Phase.WAIT_BUY;
            status = "Buying " + pendingItemId + " for $" + format(best.price());
            clickSlot(client, pendingBuySlot);
            actionCooldown = 25;
            return;
        }

        phase = Phase.ORDERS_SCAN;
        orderPage = 1;
        deliveredOrders.clear();
        client.player.connection.sendCommand("orders");
        status = "No qualifying AH flip; checking orders page 1";
        actionCooldown = 25;
    }

    private static double findPendingPrice() {
        List<Listing> list = auctionListings.get(pendingItemId);
        if (list == null) return 0;
        return list.stream().filter(x -> x.page() == targetBuyPage && x.slot() == pendingBuySlot)
                .mapToDouble(Listing::price).findFirst().orElse(0);
    }

    private static void handleConfirmation(Minecraft client) {
        var menu = client.player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            String label = stack.getHoverName().getString().trim().toLowerCase(Locale.ROOT);
            String text = (label + " " + loreText(stack)).toLowerCase(Locale.ROOT);
            if (label.equals("yes") || text.contains("confirm") || text.contains("purchase")
                    || text.contains("buy now") || text.contains("accept")) {
                clickSlot(client, i);
                status = "Confirming purchase";
                actionCooldown = 25;
                return;
            }
        }
        status = "Purchase confirmation open; waiting for Yes button";
        actionCooldown = 20;
    }

    private static boolean tryFindBoughtItem(Minecraft client) {
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(pendingItemId)) continue;
            if (i >= 9) {
                // In a server menu, the player's inventory follows the menu's own slots.
                // Translate the player-inventory index instead of clicking the same numeric slot.
                int menuSlot = client.player.containerMenu.slots.size() - 36 + i;
                if (menuSlot < 0 || menuSlot >= client.player.containerMenu.slots.size()) return false;
                clickSwapToHotbar(client, menuSlot, 8);
                status = "Moving purchased item to hotbar";
                actionCooldown = 8;
                return true;
            }
            inv.setSelectedSlot(i);
            client.player.connection.sendCommand("ah sell " + formatCommandPrice(pendingSalePrice));
            phase = Phase.WAIT_SALE;
            status = "Listing " + pendingItemId + " at $" + format(pendingSalePrice);
            actionCooldown = 35;
            return true;
        }
        return false;
    }

    private static void scanOrdersPage(Minecraft client) {
        var menu = client.player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack order = menu.slots.get(i).getItem();
            if (order.isEmpty() || isMenuControl(order)) continue;
            String lore = loreText(order).toLowerCase(Locale.ROOT);
            if (!lore.contains("click to deliver")) continue;
            if (lore.contains("delivered") && !lore.matches("(?s).*0\\s*/\\s*[1-9][0-9]*\\s+delivered.*")) continue;
            String id = BuiltInRegistries.ITEM.getKey(order.getItem()).toString();
            if (deliveredOrders.containsKey(id)) continue;
            int inventoryCount = countInventoryItem(client, id);
            if (inventoryCount <= 0) continue;
            clickSlot(client, i);
            deliveredOrders.put(id, i);
            phase = Phase.WAIT_DELIVERY;
            status = "Delivering " + order.getHoverName().getString() + " (" + inventoryCount + " available)";
            actionCooldown = 35;
            return;
        }
        int next = findNavigationSlot(client, true);
        if (next >= 0 && orderPage < MAX_PAGES) {
            clickSlot(client, next);
            orderPage++;
            status = "Checking orders page " + orderPage + "/" + MAX_PAGES;
            actionCooldown = 22;
            return;
        }
        phase = Phase.IDLE;
        status = "No deliverable orders found; restarting Auction House scan";
        actionCooldown = 25;
    }

    private static int countInventoryItem(Minecraft client, String itemId) {
        int count = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int findNavigationSlot(Minecraft client, boolean next) {
        var menu = client.player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String lore = loreText(stack).toLowerCase(Locale.ROOT);
            String words = name + " " + lore;
            if (next && (words.contains("next page") || name.contains("next"))) return i;
            if (!next && (words.contains("previous page") || words.contains("prev page")
                    || name.contains("previous") || name.contains("back"))) return i;
        }
        return -1;
    }

    private static boolean isMenuControl(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String lore = loreText(stack).toLowerCase(Locale.ROOT);
        String text = name + " " + lore;
        return text.contains("next page") || text.contains("previous page")
                || text.contains("prev page") || text.contains("click to search")
                || text.contains("click to change") || text.contains("filter")
                || text.contains("sort") || text.contains("your inventory");
    }

    private static boolean isAuctionTitle(String title) {
        return title.contains("auction") || title.equals("ah") || title.contains("market");
    }

    private static boolean isOrdersTitle(String title) {
        return title.contains("orders") || title.contains("order");
    }

    private static boolean isConfirmationTitle(String title) {
        return title.contains("confirm") || title.contains("purchase")
                || title.contains("buy") || title.contains("are you sure");
    }

    /** Only accepts an explicit dollar-denominated price in lore, never arbitrary numbers. */
    private static double readPrice(ItemStack stack) {
        String lore = loreText(stack);
        Matcher matcher = MONEY.matcher(lore);
        double value = -1;
        while (matcher.find()) {
            try {
                double parsed = Double.parseDouble(matcher.group(1).replace(",", ""));
                String suffix = matcher.group(2);
                if (suffix != null) parsed *= switch (suffix.toUpperCase(Locale.ROOT)) {
                    case "K" -> 1_000.0;
                    case "M" -> 1_000_000.0;
                    case "B" -> 1_000_000_000.0;
                    default -> 1.0;
                };
                if (parsed > value) value = parsed;
            } catch (NumberFormatException ignored) {
                // Ignore malformed currency fragments.
            }
        }
        return value;
    }

    private static String loreText(ItemStack stack) {
        StringBuilder text = new StringBuilder();
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) for (var line : lore.lines()) text.append(line.getString()).append(' ');
        return text.toString();
    }

    private static void clickSwapToHotbar(Minecraft client, int slot, int hotbarButton) {
        if (client.gameMode == null || client.player == null) return;
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId,
                slot, hotbarButton, ContainerInput.SWAP, client.player);
    }

    private static void clickSlot(Minecraft client, int slot) {
        if (client.gameMode == null || client.player == null) return;
        client.gameMode.handleContainerInput(client.player.containerMenu.containerId,
                slot, 0, ContainerInput.PICKUP, client.player);
    }

    private static String format(double value) { return String.format(Locale.US, "%,.0f", value); }
    private static String formatCommandPrice(double value) { return String.format(Locale.US, "%.0f", value); }

    private record Listing(int page, int slot, String itemId, double price, int count) {}
}
