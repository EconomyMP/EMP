package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
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

public class GemsCommand {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;

    public GemsCommand(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("gems");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.gems"));

        builder.executes(this::executeSelf);
        builder.then(buildPay());
        builder.then(buildGive());
        builder.then(buildTake());

        return builder.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildPay() {
        return Commands.literal("pay")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executePay)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildGive() {
        return Commands.literal("give")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.gems.admin"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeGive)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTake() {
        return Commands.literal("take")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.gems.admin"))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeTake)));
    }

    private int executeSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }

        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            long balance = accountService.getBalance(player.getUniqueId(), CurrencyType.GEMS);
            String formatted = accountService.getGemFormat().format(balance);
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", formatted);

            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.gems.balance.self", placeholders));
        });

        return 1;
    }

    private int executePay(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }

        Player player = (Player) sender;
        var playerSelector = context.getArgument("player",
                io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> targets;
        try {
            targets = playerSelector.resolve(context.getSource());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        if (targets.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        Player target = targets.get(0);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.getMessageService().sendMessage(sender, "emp.gems.pay_self");
            return 0;
        }

        String rawAmount = StringArgumentType.getString(context, "amount");
        long amount;
        try {
            amount = accountService.getGemFormat().parse(rawAmount);
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
                    target.getUniqueId(), target.getName(), CurrencyType.GEMS, amount, "GEMS_PAY");

            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(player, "emp.gems.insufficient");
                    return;
                }

                String formatted = accountService.getGemFormat().format(amount);
                HashMap<String, String> senderPlaceholders = new HashMap<>();
                senderPlaceholders.put("amount", formatted);
                senderPlaceholders.put("player", target.getName());

                HashMap<String, String> targetPlaceholders = new HashMap<>();
                targetPlaceholders.put("amount", formatted);
                targetPlaceholders.put("player", player.getName());

                plugin.getMessageService().sendMessage(player, "emp.gems.pay_sent", senderPlaceholders);
                plugin.getMessageService().sendMessage(target, "emp.gems.pay_received", targetPlaceholders);
            });
        });

        return 1;
    }

    private int executeGive(CommandContext<CommandSourceStack> context) {
        return handleAdmin(context, AdminAction.GIVE);
    }

    private int executeTake(CommandContext<CommandSourceStack> context) {
        return handleAdmin(context, AdminAction.TAKE);
    }

    private int handleAdmin(CommandContext<CommandSourceStack> context, AdminAction action) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, "player");
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        String displayName = target.getName() != null ? target.getName() : name;
        if (target.getUniqueId() == null) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        long amount;
        try {
            amount = accountService.getGemFormat().parse(StringArgumentType.getString(context, "amount"));
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        if (amount < 0) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
            BalanceChangeResult result = action == AdminAction.GIVE
                    ? accountService.addBalance(target.getUniqueId(), displayName, CurrencyType.GEMS, amount,
                    "GEMS_GIVE", sender.getName(), displayName)
                    : accountService.takeBalance(target.getUniqueId(), displayName, CurrencyType.GEMS, amount,
                    "GEMS_TAKE", sender.getName(), displayName);

            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(sender, "emp.gems.admin_failed");
                    return;
                }

                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("player", displayName);
                placeholders.put("amount", accountService.getGemFormat().format(amount));

                String key = action == AdminAction.GIVE ? "emp.gems.give" : "emp.gems.take";
                plugin.getMessageService().sendMessage(sender, key, placeholders);
            });
        });

        return 1;
    }

    private SuggestionProvider<CommandSourceStack> playerSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            Bukkit.getOnlinePlayers().stream()
                    .map(player -> player.getName().toLowerCase())
                    .filter(name -> name.startsWith(input))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private enum AdminAction {
        GIVE,
        TAKE
    }
}
