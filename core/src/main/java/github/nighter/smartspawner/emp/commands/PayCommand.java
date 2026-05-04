package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class PayCommand {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;

    public PayCommand(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("pay");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.pay"));

        builder.then(Commands.argument("player", ArgumentTypes.player())
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(this::executePay)));

        return builder.build();
    }

    private int executePay(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }

        Player player = (Player) sender;
        var playerSelector = context.getArgument("player",
                io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> targets = playerSelector.resolve(context.getSource());

        if (targets.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        Player target = targets.get(0);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.getMessageService().sendMessage(sender, "emp.pay.self");
            return 0;
        }

        String rawAmount = StringArgumentType.getString(context, "amount");
        long amount;
        try {
            amount = accountService.getMoneyFormat().parse(rawAmount);
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        if (amount <= 0) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
            BalanceChangeResult result = accountService.transfer(player.getUniqueId(), player.getName(),
                    target.getUniqueId(), target.getName(), CurrencyType.MONEY, amount, "PAY");

            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.pay.insufficient");
                    return;
                }

                String formatted = accountService.getMoneyFormat().format(amount);
                HashMap<String, String> senderPlaceholders = new HashMap<>();
                senderPlaceholders.put("amount", formatted);
                senderPlaceholders.put("player", target.getName());

                HashMap<String, String> targetPlaceholders = new HashMap<>();
                targetPlaceholders.put("amount", formatted);
                targetPlaceholders.put("player", player.getName());

                plugin.getMessageService().sendMessage(player, "emp.pay.sent", senderPlaceholders);
                plugin.getMessageService().sendMessage(target, "emp.pay.received", targetPlaceholders);
            });
        });

        return 1;
    }
}
