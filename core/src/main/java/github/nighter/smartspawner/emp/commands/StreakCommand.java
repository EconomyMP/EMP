package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.killstreak.KillstreakService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class StreakCommand {
    private final SmartSpawner plugin;
    private final KillstreakService killstreakService;

    public StreakCommand(SmartSpawner plugin, KillstreakService killstreakService) {
        this.plugin = plugin;
        this.killstreakService = killstreakService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("streak")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.streak"))
                .executes(context -> executeSelf(context))
                .then(Commands.literal("top").executes(context -> executeTop(context)))
                .build();
    }

    private int executeSelf(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            int current = killstreakService.getCurrentStreak(player.getUniqueId());
            int best = killstreakService.getBestStreak(player.getUniqueId());
            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("current", String.valueOf(current));
            placeholders.put("best", String.valueOf(best));
            Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.streak.self", placeholders));
        });
        return 1;
    }

    private int executeTop(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Scheduler.runTaskAsync(() -> {
            List<KillstreakService.KillstreakEntry> top = killstreakService.getTopStreaks(5);
            if (top.isEmpty()) {
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(sender, "emp.streak.empty"));
                return;
            }
            Scheduler.runTask(() -> {
                plugin.getMessageService().sendMessage(sender, "emp.streak.header");
                for (int i = 0; i < top.size(); i++) {
                    KillstreakService.KillstreakEntry entry = top.get(i);
                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("rank", String.valueOf(i + 1));
                    placeholders.put("player", entry.name());
                    placeholders.put("current", String.valueOf(entry.currentStreak()));
                    placeholders.put("best", String.valueOf(entry.bestStreak()));
                    plugin.getMessageService().sendMessage(sender, "emp.streak.entry", placeholders);
                }
            });
        });
        return 1;
    }
}
