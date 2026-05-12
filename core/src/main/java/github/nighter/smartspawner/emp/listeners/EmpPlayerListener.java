package github.nighter.smartspawner.emp.listeners;

import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.emp.bounty.BountyService;
import github.nighter.smartspawner.emp.chat.ChatPresentationService;
import github.nighter.smartspawner.emp.gems.ActivityTracker;
import github.nighter.smartspawner.emp.gems.GemRewardService;
import github.nighter.smartspawner.emp.killstreak.KillstreakService;
import github.nighter.smartspawner.emp.rank.RankService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import github.nighter.smartspawner.Scheduler;
import org.bukkit.event.player.AsyncChatEvent;

public class EmpPlayerListener implements Listener {
    private final EmpAccountService accountService;
    private final ActivityTracker activityTracker;
    private final GemRewardService gemRewardService;
    private final BountyService bountyService;
    private final KillstreakService killstreakService;
    private final ChatPresentationService chatPresentationService;
    private final RankService rankService;

    public EmpPlayerListener(EmpAccountService accountService, ActivityTracker activityTracker,
                             GemRewardService gemRewardService, BountyService bountyService,
                             KillstreakService killstreakService, ChatPresentationService chatPresentationService,
                             RankService rankService) {
        this.accountService = accountService;
        this.activityTracker = activityTracker;
        this.gemRewardService = gemRewardService;
        this.bountyService = bountyService;
        this.killstreakService = killstreakService;
        this.chatPresentationService = chatPresentationService;
        this.rankService = rankService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        accountService.ensureAccountAsync(player.getUniqueId(), player.getName());
        activityTracker.markActive(player.getUniqueId());
        if (chatPresentationService != null) {
            chatPresentationService.refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activityTracker.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (chatPresentationService == null) {
            return;
        }
        event.renderer((source, sourceDisplayName, message, viewer) -> chatPresentationService.renderChat(source, sourceDisplayName, message));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (movedBlock(event.getFrom(), event.getTo())) {
            activityTracker.markActive(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        activityTracker.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        activityTracker.markActive(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            gemRewardService.handleKill(killer, event.getEntity());
            Scheduler.runTaskAsync(() -> {
                if (killstreakService != null) {
                    killstreakService.recordKill(killer, event.getEntity());
                }
                if (bountyService != null) {
                    bountyService.claimBounty(killer, event.getEntity());
                }
            });
        }
    }

    private boolean movedBlock(Location from, Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }
}
