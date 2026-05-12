package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.manufacturing.ManufacturerService;
import github.nighter.smartspawner.emp.manufacturing.ManufacturingOrderService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class ManufacturingCommand {
    private final SmartSpawner plugin;
    private final ManufacturerService manufacturerService;
    private final ManufacturingOrderService orderService;

    public ManufacturingCommand(SmartSpawner plugin, ManufacturerService manufacturerService, ManufacturingOrderService orderService) {
        this.plugin = plugin;
        this.manufacturerService = manufacturerService;
        this.orderService = orderService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("mfg");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.mfg"));

        builder.then(Commands.literal("register")
                .then(Commands.argument("shop_name", StringArgumentType.greedyString())
                        .executes(ctx -> executeRegister(ctx))));
        builder.then(Commands.literal("add")
                .then(Commands.argument("item_name", StringArgumentType.word())
                        .then(Commands.argument("price", StringArgumentType.word())
                                .then(Commands.argument("quantity", StringArgumentType.word())
                                        .executes(ctx -> executeAdd(ctx))))));
        builder.then(Commands.literal("search")
                .then(Commands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> executeSearch(ctx))));
        builder.then(Commands.literal("shop")
                .then(Commands.argument("manufacturer", StringArgumentType.word())
                        .executes(ctx -> executeShop(ctx))));
        builder.then(Commands.literal("order")
                .then(Commands.argument("manufacturer", StringArgumentType.word())
                        .then(Commands.argument("item", StringArgumentType.word())
                                .then(Commands.argument("quantity", StringArgumentType.word())
                                        .executes(ctx -> executeOrder(ctx))))));
        builder.then(Commands.literal("orders").executes(ctx -> executeOrders(ctx)));
        builder.then(Commands.literal("deliver")
                .then(Commands.argument("order_id", StringArgumentType.word())
                        .executes(ctx -> executeDeliver(ctx))));
        builder.then(Commands.literal("inventory").executes(ctx -> executeInventory(ctx)));

        return builder.build();
    }

    private int executeRegister(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;
        String shopName = StringArgumentType.getString(ctx, "shop_name");

        Scheduler.runTaskAsync(() -> {
            ManufacturerService.ManufacturerResult result = manufacturerService.register(player, shopName);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg." + result.errorKey());
                    return;
                }
                HashMap<String, String> ph = new HashMap<>();
                ph.put("shop", result.manufacturer().shopName());
                plugin.getMessageService().sendMessage(player, "emp.mfg.registered", ph);
            });
        });
        return 1;
    }

    private int executeAdd(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;

        if (!manufacturerService.isManufacturer(player.getUniqueId())) {
            plugin.getMessageService().sendMessage(player, "emp.mfg.not_manufacturer");
            return 0;
        }

        String itemName = StringArgumentType.getString(ctx, "item_name");
        long price = parseLong(StringArgumentType.getString(ctx, "price"));
        int quantity = parseInt(StringArgumentType.getString(ctx, "quantity"));

        if (price <= 0 || quantity <= 0) {
            plugin.getMessageService().sendMessage(player, "emp.invalid_amount");
            return 0;
        }

        if (manufacturerService.addListing(player.getUniqueId(), itemName, price, quantity)) {
            HashMap<String, String> ph = new HashMap<>();
            ph.put("item", itemName);
            ph.put("price", String.valueOf(price));
            ph.put("qty", String.valueOf(quantity));
            plugin.getMessageService().sendMessage(player, "emp.mfg.listed", ph);
        } else {
            plugin.getMessageService().sendMessage(player, "emp.mfg.failed");
        }
        return 1;
    }

    private int executeSearch(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String itemName = StringArgumentType.getString(ctx, "item");

        Scheduler.runTaskAsync(() -> {
            List<ManufacturerService.ManufacturerListing> listings = manufacturerService.searchListings(itemName);
            Scheduler.runTask(() -> {
                if (listings.isEmpty()) {
                    plugin.getMessageService().sendMessage(sender, "emp.mfg.no_results");
                    return;
                }
                plugin.getMessageService().sendMessage(sender, "emp.mfg.search_header");
                for (ManufacturerService.ManufacturerListing listing : listings.stream().limit(10).toList()) {
                    var mfg = manufacturerService.getManufacturer(listing.manufacturerUuid());
                    if (mfg.isPresent()) {
                        HashMap<String, String> ph = new HashMap<>();
                        ph.put("shop", mfg.get().shopName());
                        ph.put("item", listing.itemName());
                        ph.put("price", String.valueOf(listing.price()));
                        ph.put("qty", String.valueOf(listing.quantity()));
                        plugin.getMessageService().sendMessage(sender, "emp.mfg.search_entry", ph);
                    }
                }
            });
        });
        return 1;
    }

    private int executeShop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String shopName = StringArgumentType.getString(ctx, "manufacturer");

        Scheduler.runTaskAsync(() -> {
            var mfg = manufacturerService.findByShopName(shopName);
            if (mfg.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.mfg.shop_not_found"));
                return;
            }
            List<ManufacturerService.ManufacturerListing> inventory = manufacturerService.getInventory(mfg.get().uuid());
            Scheduler.runTask(() -> {
                HashMap<String, String> ph = new HashMap<>();
                ph.put("shop", mfg.get().shopName());
                ph.put("owner", mfg.get().ownerName());
                plugin.getMessageService().sendMessage(sender, "emp.mfg.shop_header", ph);
                for (ManufacturerService.ManufacturerListing item : inventory) {
                    HashMap<String, String> itemPh = new HashMap<>();
                    itemPh.put("item", item.itemName());
                    itemPh.put("price", String.valueOf(item.price()));
                    itemPh.put("qty", String.valueOf(item.quantity()));
                    plugin.getMessageService().sendMessage(sender, "emp.mfg.shop_item", itemPh);
                }
            });
        });
        return 1;
    }

    private int executeOrder(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;

        String manufacturerName = StringArgumentType.getString(ctx, "manufacturer");
        String itemName = StringArgumentType.getString(ctx, "item");
        int quantity = parseInt(StringArgumentType.getString(ctx, "quantity"));

        if (quantity <= 0) {
            plugin.getMessageService().sendMessage(player, "emp.invalid_amount");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
            var mfg = manufacturerService.findByShopName(manufacturerName);
            if (mfg.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.mfg.shop_not_found"));
                return;
            }
            var listings = manufacturerService.getInventory(mfg.get().uuid()).stream()
                    .filter(l -> l.itemName().equalsIgnoreCase(itemName))
                    .findFirst();
            if (listings.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.mfg.item_not_found"));
                return;
            }
            ManufacturerService.ManufacturerListing listing = listings.get();
            if (listing.quantity() < quantity) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.mfg.insufficient_stock"));
                return;
            }
            ManufacturingOrderService.OrderResult result = orderService.placeOrder(player, mfg.get().uuid(), mfg.get().ownerName(), itemName, quantity, listing.price());
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg." + result.errorKey());
                    return;
                }
                HashMap<String, String> ph = new HashMap<>();
                ph.put("manufacturer", manufacturerName);
                ph.put("item", itemName);
                ph.put("qty", String.valueOf(quantity));
                plugin.getMessageService().sendMessage(player, "emp.mfg.order_placed", ph);
            });
        });
        return 1;
    }

    private int executeOrders(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;

        Scheduler.runTaskAsync(() -> {
            var buyerOrders = orderService.getBuyerOrders(player.getUniqueId());
            var manufacturerOrders = manufacturerService.isManufacturer(player.getUniqueId()) 
                    ? orderService.getManufacturerOrders(player.getUniqueId()) 
                    : List.of();

            Scheduler.runTask(() -> {
                if (!buyerOrders.isEmpty()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg.buyer_orders");
                    for (ManufacturingOrderService.ManufacturingOrder order : buyerOrders) {
                        HashMap<String, String> ph = new HashMap<>();
                        ph.put("id", String.valueOf(order.id()));
                        ph.put("manufacturer", order.manufacturerName());
                        ph.put("item", order.itemName());
                        ph.put("qty", String.valueOf(order.quantity()));
                        ph.put("status", order.status());
                        plugin.getMessageService().sendMessage(player, "emp.mfg.order_entry", ph);
                    }
                }
                if (!manufacturerOrders.isEmpty()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg.manufacturer_orders");
                    for (ManufacturingOrderService.ManufacturingOrder order : manufacturerOrders) {
                        HashMap<String, String> ph = new HashMap<>();
                        ph.put("id", String.valueOf(order.id()));
                        ph.put("buyer", order.buyerName());
                        ph.put("item", order.itemName());
                        ph.put("qty", String.valueOf(order.quantity()));
                        ph.put("status", order.status());
                        plugin.getMessageService().sendMessage(player, "emp.mfg.order_entry", ph);
                    }
                }
                if (buyerOrders.isEmpty() && manufacturerOrders.isEmpty()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg.no_orders");
                }
            });
        });
        return 1;
    }

    private int executeDeliver(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;

        if (!manufacturerService.isManufacturer(player.getUniqueId())) {
            plugin.getMessageService().sendMessage(player, "emp.mfg.not_manufacturer");
            return 0;
        }

        long orderId = parseLong(StringArgumentType.getString(ctx, "order_id"));
        Scheduler.runTaskAsync(() -> {
            ManufacturingOrderService.OrderResult result = orderService.completeOrder(player.getUniqueId(), orderId);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg." + result.errorKey());
                    return;
                }
                HashMap<String, String> ph = new HashMap<>();
                ph.put("id", String.valueOf(orderId));
                plugin.getMessageService().sendMessage(player, "emp.mfg.order_completed", ph);
            });
        });
        return 1;
    }

    private int executeInventory(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) return 0;
        Player player = (Player) sender;

        if (!manufacturerService.isManufacturer(player.getUniqueId())) {
            plugin.getMessageService().sendMessage(player, "emp.mfg.not_manufacturer");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
            List<ManufacturerService.ManufacturerListing> inventory = manufacturerService.getInventory(player.getUniqueId());
            Scheduler.runTask(() -> {
                plugin.getMessageService().sendMessage(player, "emp.mfg.inventory_header");
                if (inventory.isEmpty()) {
                    plugin.getMessageService().sendMessage(player, "emp.mfg.inventory_empty");
                } else {
                    for (ManufacturerService.ManufacturerListing item : inventory) {
                        HashMap<String, String> ph = new HashMap<>();
                        ph.put("item", item.itemName());
                        ph.put("price", String.valueOf(item.price()));
                        ph.put("qty", String.valueOf(item.quantity()));
                        plugin.getMessageService().sendMessage(player, "emp.mfg.inventory_item", ph);
                    }
                }
            });
        });
        return 1;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
