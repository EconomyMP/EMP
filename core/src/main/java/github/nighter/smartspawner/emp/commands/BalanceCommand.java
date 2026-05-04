package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class BalanceCommand {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;

    public BalanceCommand(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build(String name) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(name);
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.bal"));

        builder.executes(this::executeSelf);

        builder.then(Commands.argument("player", ArgumentTypes.player())
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.bal.other"))
                .executes(this::executeOther));

        return builder.build();
    }

    private int executeSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }

        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            long balance = accountService.getBalance(player.getUniqueId(), CurrencyType.MONEY);
            String formatted = accountService.getMoneyFormat().format(balance);
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", formatted);

            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.balance.self", placeholders));
        });

        return 1;
    }

    private int executeOther(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        var playerSelector = context.getArgument("player",
                io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> players = playerSelector.resolve(context.getSource());

        if (players.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        Player target = players.get(0);
        Scheduler.runTaskAsync(() -> {
            long balance = accountService.getBalance(target.getUniqueId(), CurrencyType.MONEY);
            String formatted = accountService.getMoneyFormat().format(balance);
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", formatted);
            placeholders.put("player", target.getName());

            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.balance.other", placeholders));
        });

        return 1;
    }
}
