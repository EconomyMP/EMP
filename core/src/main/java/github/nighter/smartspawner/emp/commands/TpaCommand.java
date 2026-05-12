package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.tpa.TeleportRequestService;
import github.nighter.smartspawner.emp.tpa.TeleportRequestService.TpaRequest;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class TpaCommand {
    private final SmartSpawner plugin;
    private final TeleportRequestService teleportRequestService;

    public TpaCommand(SmartSpawner plugin, TeleportRequestService teleportRequestService) {
        this.plugin = plugin;
        this.teleportRequestService = teleportRequestService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("tpa");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.tpa"));
        builder.then(Commands.argument("player", ArgumentTypes.player()).executes(this::execute));
        return builder.build();
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }

        Player requester = (Player) sender;
        var selector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> targets = selector.resolve(context.getSource());
        if (targets.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }

        Player target = targets.get(0);
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            plugin.getMessageService().sendMessage(sender, "emp.tpa.self");
            return 0;
        }

        TpaRequest request = teleportRequestService.request(requester, target);
        if (request.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.tpa.already_sent");
            return 0;
        }

        if ("SELF".equals(request.status())) {
            plugin.getMessageService().sendMessage(sender, "emp.tpa.self");
            return 0;
        }
        if ("ALREADY".equals(request.status())) {
            plugin.getMessageService().sendMessage(sender, "emp.tpa.already_sent");
            return 0;
        }
        if ("BUSY".equals(request.status())) {
            plugin.getMessageService().sendMessage(sender, "emp.tpa.target_busy");
            return 0;
        }

        HashMap<String, String> requesterPlaceholders = new HashMap<>();
        requesterPlaceholders.put("player", target.getName());
        plugin.getMessageService().sendMessage(requester, "emp.tpa.sent", requesterPlaceholders);

        HashMap<String, String> targetPlaceholders = new HashMap<>();
        targetPlaceholders.put("player", requester.getName());
        plugin.getMessageService().sendMessage(target, "emp.tpa.received", targetPlaceholders);
        return 1;
    }
}
