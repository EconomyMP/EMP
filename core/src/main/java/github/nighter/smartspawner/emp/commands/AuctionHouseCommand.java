package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.auction.AuctionHouseService;
import github.nighter.smartspawner.emp.auction.AuctionHouseService.AuctionListing;
import github.nighter.smartspawner.emp.auction.AuctionHouseService.AuctionResult;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class AuctionHouseCommand {
    private final SmartSpawner plugin;
    private final AuctionHouseService auctionHouseService;
    private final EmpAccountService accountService;

    public AuctionHouseCommand(SmartSpawner plugin, AuctionHouseService auctionHouseService, EmpAccountService accountService) {
        this.plugin = plugin;
        this.auctionHouseService = auctionHouseService;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("ah");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.ah"));
        builder.executes(context -> executeList(context, 1));
        builder.then(Commands.literal("list")
            .then(Commands.argument("page", StringArgumentType.word()).executes(context -> executeList(context, parsePage(context, "page")))));
        builder.then(Commands.literal("sell")
            .then(Commands.argument("price", StringArgumentType.word()).executes(this::executeSell)));
        builder.then(Commands.literal("buy")
            .then(Commands.argument("id", StringArgumentType.word()).executes(this::executeBuy)));
        builder.then(Commands.literal("cancel")
            .then(Commands.argument("id", StringArgumentType.word()).executes(this::executeCancel)));
        return builder.build();
    }

    private int executeList(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();
        Scheduler.runTaskAsync(() -> {
            List<AuctionListing> listings = auctionHouseService.getListings(10 * page);
            if (listings.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.ah.empty"));
                return;
            }
            Scheduler.runTask(() -> {
                plugin.getMessageService().sendMessage(sender, "emp.ah.header");
                for (AuctionListing listing : listings) {
                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("id", String.valueOf(listing.id()));
                    placeholders.put("seller", listing.sellerName());
                    placeholders.put("item", listing.item().getType().name());
                    placeholders.put("price", accountService.getMoneyFormat().format(listing.price()));
                    plugin.getMessageService().sendMessage(sender, "emp.ah.entry", placeholders);
                }
            });
        });
        return 1;
    }

    private int executeSell(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        long price;
        try {
            price = accountService.getMoneyFormat().parse(StringArgumentType.getString(context, "price"));
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }
        Scheduler.runTaskAsync(() -> {
            AuctionResult result = auctionHouseService.listHeldItem(player, price);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.ah." + result.errorKey());
                    return;
                }
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("id", String.valueOf(result.id()));
                placeholders.put("item", result.item().getType().name());
                placeholders.put("price", accountService.getMoneyFormat().format(result.price()));
                plugin.getMessageService().sendMessage(player, "emp.ah.listed", placeholders);
            });
        });
        return 1;
    }

    private int executeBuy(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        long id = parseLong(context, "id");
        Scheduler.runTaskAsync(() -> {
            AuctionResult result = auctionHouseService.buy(player, id);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, mapAuctionError(result.errorKey()));
                    return;
                }
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("id", String.valueOf(result.id()));
                placeholders.put("price", accountService.getMoneyFormat().format(result.price()));
                placeholders.put("item", result.item().getType().name());
                plugin.getMessageService().sendMessage(player, "emp.ah.bought", placeholders);
            });
        });
        return 1;
    }

    private int executeCancel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        long id = parseLong(context, "id");
        Scheduler.runTaskAsync(() -> {
            AuctionResult result = auctionHouseService.cancel(player, id);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, mapAuctionError(result.errorKey()));
                    return;
                }
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("id", String.valueOf(result.id()));
                placeholders.put("item", result.item().getType().name());
                plugin.getMessageService().sendMessage(player, "emp.ah.cancelled", placeholders);
            });
        });
        return 1;
    }

    private int parsePage(CommandContext<CommandSourceStack> context, String name) {
        try {
            return Math.max(1, Integer.parseInt(StringArgumentType.getString(context, name)));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private long parseLong(CommandContext<CommandSourceStack> context, String name) {
        try {
            return Long.parseLong(StringArgumentType.getString(context, name));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String mapAuctionError(String errorKey) {
        return switch (errorKey) {
            case "no_item" -> "emp.ah.no_item";
            case "invalid_price" -> "emp.ah.invalid_price";
            case "not_found" -> "emp.ah.not_found";
            case "self_buy" -> "emp.ah.self_buy";
            case "expired" -> "emp.ah.expired";
            case "insufficient" -> "emp.ah.insufficient";
            case "not_owner" -> "emp.ah.not_owner";
            case "sold" -> "emp.ah.sold";
            default -> "emp.ah.failed";
        };
    }
}
