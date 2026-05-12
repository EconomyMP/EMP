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

public class TpaDenyCommand {
    private final SmartSpawner plugin;
    private final TeleportRequestService teleportRequestService;

    public TpaDenyCommand(SmartSpawner plugin, TeleportRequestService teleportRequestService) {
        this.plugin = plugin;
        this.teleportRequestService = teleportRequestService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpdeny")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.tpa"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
                        return 0;
                    }

                    Player target = (Player) sender;
                    TpaRequest request = teleportRequestService.deny(target);
                    if (request.isEmpty()) {
                        plugin.getMessageService().sendMessage(sender, "emp.tpa.none");
                        return 0;
                    }

                    HashMap<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", request.requesterName());
                    plugin.getMessageService().sendMessage(sender, "emp.tpa.denied", placeholders);

                    Player requester = plugin.getServer().getPlayer(request.requesterUuid());
                    if (requester != null && requester.isOnline()) {
                        plugin.getMessageService().sendMessage(requester, "emp.tpa.denied_by_target", placeholders);
                    }
                    return 1;
                }).build();
    }
}
