package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.bounty.BountyService;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class BountyCommand {
    private final SmartSpawner plugin;
    private final BountyService bountyService;
    private final EmpAccountService accountService;

    public BountyCommand(SmartSpawner plugin, BountyService bountyService, EmpAccountService accountService) {
        this.plugin = plugin;
        this.bountyService = bountyService;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("bounty");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.bounty"));
        builder.executes(this::executeView);
        builder.then(Commands.literal("add")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeAdd))));
        builder.then(Commands.literal("top").executes(this::executeTop));
        return builder.build();
    }

    private int executeView(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            long bounty = bountyService.getBounty(player.getUniqueId());
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", accountService.getMoneyFormat().format(bounty));
            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.bounty.self", placeholders));
        });
        return 1;
    }

    private int executeAdd(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        var selector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> targets = selector.resolve(context.getSource());
        if (targets.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }
        Player target = targets.get(0);

        long amount;
        try {
            amount = accountService.getMoneyFormat().parse(StringArgumentType.getString(context, "amount"));
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
            BountyService.BountyResult result = bountyService.placeBounty(player, target, amount);
            Scheduler.runTask(() -> {
                if (!result.success()) {
                    String key = switch (result.errorKey()) {
                        case "self" -> "emp.bounty.self_error";
                        case "insufficient" -> "emp.bounty.insufficient";
                        case "invalid_amount" -> "emp.bounty.invalid_amount";
                        default -> "emp.bounty.invalid";
                    };
                    plugin.getMessageService().sendMessage(sender, key);
                    return;
                }
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("player", target.getName());
                placeholders.put("amount", accountService.getMoneyFormat().format(amount));
                plugin.getMessageService().sendMessage(sender, "emp.bounty.placed", placeholders);
            });
        });
        return 1;
    }

    private int executeTop(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Scheduler.runTaskAsync(() -> {
            List<BountyService.BountyEntry> top = bountyService.getTopBounties(5);
            if (top.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.bounty.empty"));
                return;
            }
            Scheduler.runTask(() -> {
                plugin.getMessageService().sendMessage(sender, "emp.bounty.header");
                for (int i = 0; i < top.size(); i++) {
                    BountyService.BountyEntry entry = top.get(i);
                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("rank", String.valueOf(i + 1));
                    placeholders.put("player", entry.targetName());
                    placeholders.put("amount", accountService.getMoneyFormat().format(entry.amount()));
                    plugin.getMessageService().sendMessage(sender, "emp.bounty.entry", placeholders);
                }
            });
        });
        return 1;
    }
}
