package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.tpa.TeleportRequestService;
import github.nighter.smartspawner.emp.tpa.TeleportRequestService.TpaRequest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class TpaCancelCommand {
    private final SmartSpawner plugin;
    private final TeleportRequestService teleportRequestService;

    public TpaCancelCommand(SmartSpawner plugin, TeleportRequestService teleportRequestService) {
        this.plugin = plugin;
        this.teleportRequestService = teleportRequestService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpcancel")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.tpa"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
                        return 0;
                    }

                    Player requester = (Player) sender;
                    TpaRequest request = teleportRequestService.cancel(requester);
                    if (request.isEmpty()) {
                        plugin.getMessageService().sendMessage(sender, "emp.tpa.none_sent");
                        return 0;
                    }

                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", request.targetName());
                    plugin.getMessageService().sendMessage(sender, "emp.tpa.cancelled", placeholders);
                    return 1;
                }).build();
    }
}
