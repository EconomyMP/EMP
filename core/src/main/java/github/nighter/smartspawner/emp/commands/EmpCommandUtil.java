package github.nighter.smartspawner.emp.commands;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

public final class EmpCommandUtil {
    private EmpCommandUtil() {
    }

    public static boolean hasPermission(CommandSender sender, String permission) {
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return true;
        }
        return sender.hasPermission(permission) || sender.isOp();
    }

    public static boolean requirePlayer(SmartSpawner plugin, CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }
        plugin.getMessageService().sendMessage(sender, "emp.player_only");
        return false;
    }

    public static void sendNoPermission(SmartSpawner plugin, CommandSender sender) {
        plugin.getMessageService().sendMessage(sender, "emp.no_permission");
    }
}
