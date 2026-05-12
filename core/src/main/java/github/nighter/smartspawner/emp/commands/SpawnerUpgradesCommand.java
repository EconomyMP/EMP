package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.spawner.gui.EmpSpawnerGuiService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnerUpgradesCommand {
    private final SmartSpawner plugin;
    private final EmpSpawnerGuiService guiService;

    public SpawnerUpgradesCommand(SmartSpawner plugin, EmpSpawnerGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawnerupgrades")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.spawnerupgrades"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        plugin.getMessageService().sendMessage(sender, "emp.player_only");
                        return 0;
                    }

                    Block target = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
                    if (target == null || target.getType() != org.bukkit.Material.SPAWNER) {
                        plugin.getMessageService().sendMessage(player, "emp.spawners.upgrade.look_at_spawner");
                        return 0;
                    }

                    SpawnerData spawner = plugin.getSpawnerManager().getSpawnerByLocation(target.getLocation());
                    if (spawner == null) {
                        plugin.getMessageService().sendMessage(player, "emp.spawners.upgrade.not_emp_spawner");
                        return 0;
                    }

                    guiService.openUpgrade(player, spawner);
                    return 1;
                }).build();
    }
}
