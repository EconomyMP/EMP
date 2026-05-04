package github.nighter.smartspawner.emp.listeners;

import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.emp.gems.ActivityTracker;
import github.nighter.smartspawner.emp.gems.GemRewardService;
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

public class EmpPlayerListener implements Listener {
    private final EmpAccountService accountService;
    private final ActivityTracker activityTracker;
    private final GemRewardService gemRewardService;

    public EmpPlayerListener(EmpAccountService accountService, ActivityTracker activityTracker,
                             GemRewardService gemRewardService) {
        this.accountService = accountService;
        this.activityTracker = activityTracker;
        this.gemRewardService = gemRewardService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        accountService.ensureAccountAsync(player.getUniqueId(), player.getName());
        activityTracker.markActive(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activityTracker.remove(event.getPlayer().getUniqueId());
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
