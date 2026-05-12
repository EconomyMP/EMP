package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.team.TeamService;
import github.nighter.smartspawner.emp.team.TeamService.TeamInvite;
import github.nighter.smartspawner.emp.team.TeamService.TeamMembership;
import github.nighter.smartspawner.emp.team.TeamService.TeamResult;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

public class TeamCommand {
    private final SmartSpawner plugin;
    private final TeamService teamService;

    public TeamCommand(SmartSpawner plugin, TeamService teamService) {
        this.plugin = plugin;
        this.teamService = teamService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("team");
        builder.requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.team"));
        builder.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(context -> executeCreate(context))));
        builder.then(Commands.literal("invite")
                .then(Commands.argument("player", ArgumentTypes.player()).executes(context -> executeInvite(context))));
        builder.then(Commands.literal("accept").executes(context -> executeAccept(context)));
        builder.then(Commands.literal("leave").executes(context -> executeLeave(context)));
        builder.then(Commands.literal("disband").executes(context -> executeDisband(context)));
        builder.then(Commands.literal("info").executes(context -> executeInfo(context)));
        return builder.build();
    }

    private int executeCreate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        String name = StringArgumentType.getString(context, "name");
        TeamResult result = teamService.createTeam(player, name);
        if (!result.success()) {
            plugin.getMessageService().sendMessage(sender, "emp.team." + result.errorKey());
            return 0;
        }
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("team", result.membership().teamName());
        plugin.getMessageService().sendMessage(sender, "emp.team.created", placeholders);
        return 1;
    }

    private int executeInvite(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        var selector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
        List<Player> targets = selector.resolve(context.getSource());
        if (targets.isEmpty()) {
            plugin.getMessageService().sendMessage(sender, "emp.player_not_found");
            return 0;
        }
        Player target = targets.get(0);
        TeamResult result = teamService.invite(player, target);
        if (!result.success()) {
            plugin.getMessageService().sendMessage(sender, "emp.team." + result.errorKey());
            return 0;
        }
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("player", target.getName());
        plugin.getMessageService().sendMessage(sender, "emp.team.invited", placeholders);
        plugin.getMessageService().sendMessage(target, "emp.team.invited_target", placeholders);
        return 1;
    }

    private int executeAccept(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        TeamResult result = teamService.acceptInvite(player);
        if (!result.success()) {
            plugin.getMessageService().sendMessage(sender, "emp.team." + result.errorKey());
            return 0;
        }
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("team", result.membership().teamName());
        plugin.getMessageService().sendMessage(sender, "emp.team.accepted", placeholders);
        return 1;
    }

    private int executeLeave(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        TeamResult result = teamService.leave(player);
        if (!result.success()) {
            plugin.getMessageService().sendMessage(sender, "emp.team." + result.errorKey());
            return 0;
        }
        plugin.getMessageService().sendMessage(sender, "emp.team.left");
        return 1;
    }

    private int executeDisband(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        TeamResult result = teamService.disband(player);
        if (!result.success()) {
            plugin.getMessageService().sendMessage(sender, "emp.team." + result.errorKey());
            return 0;
        }
        plugin.getMessageService().sendMessage(sender, "emp.team.disbanded");
        return 1;
    }

    private int executeInfo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!EmpCommandUtil.requirePlayer(plugin, sender)) {
            return 0;
        }
        Player player = (Player) sender;
        Scheduler.runTaskAsync(() -> {
            teamService.getMembership(player.getUniqueId()).ifPresentOrElse(membership -> {
                List<TeamMembership> members = teamService.getTeamMembers(player.getUniqueId());
                HashMap<String, String> header = new HashMap<>();
                header.put("team", membership.teamName());
                header.put("count", String.valueOf(members.size()));
                Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.team.info_header", header));
                for (TeamMembership member : members) {
                    HashMap<String, String> line = new HashMap<>();
                    line.put("player", member.memberName());
                    line.put("role", member.role());
                    Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.team.info_member", line));
                }
            }, () -> Scheduler.runTask(() -> plugin.getMessageService().sendMessage(player, "emp.team.not_in_team")));
        });
        return 1;
    }
}
