package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.rank.RankService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class RankCommand {
    private final SmartSpawner plugin;
    private final RankService rankService;

    public RankCommand(SmartSpawner plugin, RankService rankService) {
        this.plugin = plugin;
        this.rankService = rankService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rank")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.rank"))
                .executes(context -> executeSelf(context))
                .build();
    }

    private int executeSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            RankService.RankDefinition current = rankService.getCurrentRank(player.getUniqueId());
            RankService.RankDefinition next = rankService.getNextRank(player.getUniqueId());
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("rank", current.displayName());
            placeholders.put("description", current.description());
            if (next != null) {
                placeholders.put("next", next.displayName());
                placeholders.put("next_threshold", String.valueOf(next.thresholdBalance()));
            }
            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.rank.self", placeholders));
        });
        return 1;
    }
}
