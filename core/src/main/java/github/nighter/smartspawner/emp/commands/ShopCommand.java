package github.nighter.smartspawner.emp.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.spawner.gui.EmpSpawnerGuiService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand {
    private final SmartSpawner plugin;
    private final EmpSpawnerGuiService guiService;

    public ShopCommand(SmartSpawner plugin, EmpSpawnerGuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shop")
                .requires(source -> EmpCommandUtil.hasPermission(source.getSender(), "emp.command.shop"))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        plugin.getMessageService().sendMessage(sender, "emp.player_only");
                        return 0;
                    }
                    guiService.openShop(player);
                    return 1;
                }).build();
    }
}
