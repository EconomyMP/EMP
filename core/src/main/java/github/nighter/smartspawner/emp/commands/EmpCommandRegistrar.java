package github.nighter.smartspawner.emp.commands;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import io.papermc.paper.command.brigadier.Commands;

public class EmpCommandRegistrar {
    private final BalanceCommand balanceCommand;
    private final PayCommand payCommand;
    private final BalTopCommand balTopCommand;
    private final EcoCommand ecoCommand;
    private final GemsCommand gemsCommand;

    public EmpCommandRegistrar(SmartSpawner plugin, EmpAccountService accountService) {
        this.balanceCommand = new BalanceCommand(plugin, accountService);
        this.payCommand = new PayCommand(plugin, accountService);
        this.balTopCommand = new BalTopCommand(plugin, accountService);
        this.ecoCommand = new EcoCommand(plugin, accountService);
        this.gemsCommand = new GemsCommand(plugin, accountService);
    }

    public void register(Commands commands) {
        commands.register(balanceCommand.build("bal"), "EMP balance command");
        commands.register(balanceCommand.build("balance"), "EMP balance alias");
        commands.register(payCommand.build(), "EMP pay command");
        commands.register(balTopCommand.build(), "EMP baltop command");
        commands.register(ecoCommand.build(), "EMP economy admin command");
        commands.register(gemsCommand.build(), "EMP gems command");
    }
}
