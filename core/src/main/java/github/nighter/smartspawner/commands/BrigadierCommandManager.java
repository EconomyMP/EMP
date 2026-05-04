package github.nighter.smartspawner.commands;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.commands.EmpCommandRegistrar;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class BrigadierCommandManager {
    private final SmartSpawner plugin;
    private final MainCommand mainCommand;
    private final EmpCommandRegistrar empCommandRegistrar;

    public BrigadierCommandManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.mainCommand = new MainCommand(plugin);
        if (plugin.getEmpAccountService() != null) {
            this.empCommandRegistrar = new EmpCommandRegistrar(plugin, plugin.getEmpAccountService());
        } else {
            this.empCommandRegistrar = null;
        }
    }

    /**
     * Register all Brigadier commands with Paper's command system
     */
    public void registerCommands() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();

        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register main command
            commands.register(mainCommand.buildCommand(), "Main SmartSpawner command");

            // Register aliases
            commands.register(mainCommand.buildAliasCommand(), "SmartSpawner command alias");
            commands.register(mainCommand.buildAliasCommand2(), "SmartSpawner command short alias");

            if (empCommandRegistrar != null) {
                empCommandRegistrar.register(commands);
            }
        });
    }
}
    