package github.nighter.smartspawner.emp.commands;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.emp.spawner.gui.EmpSpawnerGuiService;
import github.nighter.smartspawner.emp.bounty.BountyService;
import github.nighter.smartspawner.emp.auction.AuctionHouseService;
import github.nighter.smartspawner.emp.killstreak.KillstreakService;
import github.nighter.smartspawner.emp.rank.RankService;
import github.nighter.smartspawner.emp.team.TeamService;
import github.nighter.smartspawner.emp.tpa.TeleportRequestService;
import io.papermc.paper.command.brigadier.Commands;

public class EmpCommandRegistrar {
    private final BalanceCommand balanceCommand;
    private final PayCommand payCommand;
    private final BalTopCommand balTopCommand;
    private final EcoCommand ecoCommand;
    private final GemsCommand gemsCommand;
    private final ShopCommand shopCommand;
    private final SpawnerShopCommand spawnerShopCommand;
    private final SpawnerUpgradesCommand spawnerUpgradesCommand;
    private final TpaCommand tpaCommand;
    private final TpaAcceptCommand tpaAcceptCommand;
    private final TpaDenyCommand tpaDenyCommand;
    private final TpaCancelCommand tpaCancelCommand;
    private final TeamCommand teamCommand;
    private final BountyCommand bountyCommand;
    private final StreakCommand streakCommand;
    private final AuctionHouseCommand auctionHouseCommand;
    private final RankCommand rankCommand;
    private final ManufacturingCommand manufacturingCommand;

    public EmpCommandRegistrar(SmartSpawner plugin, EmpAccountService accountService, EmpSpawnerGuiService spawnerGuiService,
                               TeleportRequestService teleportRequestService, TeamService teamService,
                               BountyService bountyService, KillstreakService killstreakService,
                               AuctionHouseService auctionHouseService, RankService rankService,
                               github.nighter.smartspawner.emp.manufacturing.ManufacturerService manufacturerService,
                               github.nighter.smartspawner.emp.manufacturing.ManufacturingOrderService manufacturingOrderService) {
        this.balanceCommand = new BalanceCommand(plugin, accountService);
        this.payCommand = new PayCommand(plugin, accountService);
        this.balTopCommand = new BalTopCommand(plugin, accountService);
        this.ecoCommand = new EcoCommand(plugin, accountService);
        this.gemsCommand = new GemsCommand(plugin, accountService);
        this.shopCommand = new ShopCommand(plugin, spawnerGuiService);
        this.spawnerShopCommand = new SpawnerShopCommand(plugin, spawnerGuiService);
        this.spawnerUpgradesCommand = new SpawnerUpgradesCommand(plugin, spawnerGuiService);
        this.tpaCommand = new TpaCommand(plugin, teleportRequestService);
        this.tpaAcceptCommand = new TpaAcceptCommand(plugin, teleportRequestService);
        this.tpaDenyCommand = new TpaDenyCommand(plugin, teleportRequestService);
        this.tpaCancelCommand = new TpaCancelCommand(plugin, teleportRequestService);
        this.teamCommand = new TeamCommand(plugin, teamService);
        this.bountyCommand = new BountyCommand(plugin, bountyService, accountService);
        this.streakCommand = new StreakCommand(plugin, killstreakService);
        this.auctionHouseCommand = new AuctionHouseCommand(plugin, auctionHouseService, accountService);
        this.rankCommand = new RankCommand(plugin, rankService);
        this.manufacturingCommand = new ManufacturingCommand(plugin, manufacturerService, manufacturingOrderService);
    }

    public void register(Commands commands) {
        commands.register(balanceCommand.build("bal"), "EMP balance command");
        commands.register(balanceCommand.build("balance"), "EMP balance alias");
        commands.register(payCommand.build(), "EMP pay command");
        commands.register(balTopCommand.build(), "EMP baltop command");
        commands.register(ecoCommand.build(), "EMP economy admin command");
        commands.register(gemsCommand.build(), "EMP gems command");
        commands.register(shopCommand.build(), "EMP shop command");
        commands.register(spawnerShopCommand.build(), "EMP spawner shop command");
        commands.register(spawnerUpgradesCommand.build(), "EMP spawner upgrades command");
        commands.register(tpaCommand.build(), "EMP tpa command");
        commands.register(tpaAcceptCommand.build(), "EMP tpa accept command");
        commands.register(tpaDenyCommand.build(), "EMP tpa deny command");
        commands.register(tpaCancelCommand.build(), "EMP tpa cancel command");
        commands.register(teamCommand.build(), "EMP team command");
        commands.register(bountyCommand.build(), "EMP bounty command");
        commands.register(streakCommand.build(), "EMP streak command");
        commands.register(auctionHouseCommand.build(), "EMP auction house command");
        commands.register(rankCommand.build(), "EMP rank command");
        commands.register(manufacturingCommand.build(), "EMP manufacturing command");
    }
}
