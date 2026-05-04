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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.HashMap;

public class EcoCommand {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;

    public EcoCommand(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("eco");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.eco"));

        builder.then(buildGive());
        builder.then(buildTake());
        builder.then(buildSet());

        return builder.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildGive() {
        return Commands.literal("give")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeGive)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildTake() {
        return Commands.literal("take")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeTake)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildSet() {
        return Commands.literal("set")
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(playerSuggestions())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(this::executeSet)));
    }

    private int executeGive(CommandContext<CommandSourceStack> context) {
        return handleChange(context, ChangeType.GIVE);
    }

    private int executeTake(CommandContext<CommandSourceStack> context) {
        return handleChange(context, ChangeType.TAKE);
    }

    private int executeSet(CommandContext<CommandSourceStack> context) {
        return handleChange(context, ChangeType.SET);
    }

    private int handleChange(CommandContext<CommandSourceStack> context, ChangeType type) {
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
            amount = accountService.getMoneyFormat().parse(StringArgumentType.getString(context, "amount"));
        } catch (IllegalArgumentException e) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        if (amount < 0) {
            plugin.getMessageService().sendMessage(sender, "emp.invalid_amount");
            return 0;
        }

        Scheduler.runTaskAsync(() -> {
                BalanceChangeResult result = switch (type) {
                case GIVE -> accountService.addBalance(target.getUniqueId(), displayName, CurrencyType.MONEY,
                    amount, "ECO_GIVE", sender.getName(), displayName);
                case TAKE -> accountService.takeBalance(target.getUniqueId(), displayName, CurrencyType.MONEY,
                    amount, "ECO_TAKE", sender.getName(), displayName);
                case SET -> accountService.setBalance(target.getUniqueId(), displayName, CurrencyType.MONEY,
                    amount, "ECO_SET", sender.getName(), displayName);
            };

            Scheduler.runTask(() -> {
                if (!result.success()) {
                    plugin.getMessageService().sendMessage(sender, "emp.eco.failed");
                    return;
                }

                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("player", displayName);
                placeholders.put("amount", accountService.getMoneyFormat().format(amount));

                String key = switch (type) {
                    case GIVE -> "emp.eco.give";
                    case TAKE -> "emp.eco.take";
                    case SET -> "emp.eco.set";
                };

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

    private enum ChangeType {
        GIVE,
        TAKE,
        SET
    }
}
