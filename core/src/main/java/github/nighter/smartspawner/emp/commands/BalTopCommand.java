package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceEntry;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;

public class BalTopCommand {
    private final SmartSpawner plugin;
    private final EmpAccountService accountService;

    public BalTopCommand(SmartSpawner plugin, EmpAccountService accountService) {
        this.plugin = plugin;
        this.accountService = accountService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("baltop");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.baltop"));

        builder.executes(context -> execute(context, 1));
        builder.then(Commands.argument("page", IntegerArgumentType.integer(1))
                .executes(context -> execute(context, IntegerArgumentType.getInteger(context, "page"))));

        return builder.build();
    }

    private int execute(CommandContext<CommandSourceStack> context, int page) {
        CommandSender sender = context.getSource().getSender();

        Scheduler.runTaskAsync(() -> {
            int pageSize = plugin.getEmpConfig().getConfig().getInt("economy.baltop.page_size", 10);
            int total = accountService.getAccountCount();
            int maxPage = Math.max(1, (int) Math.ceil(total / (double) pageSize));

            if (page > maxPage) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.baltop.invalid_page"));
                return;
            }

            int offset = (page - 1) * pageSize;
            List<BalanceEntry> entries = accountService.getTopBalances(pageSize, offset);

            Scheduler.runTask(() -> sendEntries(sender, entries, page, maxPage, pageSize));
        });

        return 1;
    }

    private void sendEntries(CommandSender sender, List<BalanceEntry> entries, int page, int maxPage, int pageSize) {
        if (entries.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.baltop.empty");
            return;
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("page", String.valueOf(page));
        header.put("max_page", String.valueOf(maxPage));
        plugin.getMessageService().sendMessage(sender, "emp.baltop.header", header);

        int rank = (page - 1) * pageSize + 1;
        for (BalanceEntry entry : entries) {
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("rank", String.valueOf(rank++));
            placeholders.put("player", entry.name());
            placeholders.put("amount", accountService.getMoneyFormat().format(entry.balance()));
            plugin.getMessageService().sendMessage(sender, "emp.baltop.entry", placeholders);
        }

        HashMap<String, String> footer = new HashMap<>();
        footer.put("page", String.valueOf(page));
        footer.put("max_page", String.valueOf(maxPage));
        plugin.getMessageService().sendMessage(sender, "emp.baltop.footer", footer);
    }
}
