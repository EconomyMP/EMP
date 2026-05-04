package github.nighter.smartspawner.emp.gems;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.config.EmpConfig;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GemRewardService {
    private final SmartSpawner plugin;
    private final EmpConfig empConfig;
    private final EmpAccountService accountService;
    private final ActivityTracker activityTracker;
    private final GemEventService eventService;
    private final KillRewardGuard killRewardGuard = new KillRewardGuard();

    private Scheduler.Task playtimeTask;
    private Scheduler.Task eventCheckTask;
    private boolean lastEventActive;

    public GemRewardService(SmartSpawner plugin, EmpConfig empConfig, EmpAccountService accountService,
                            ActivityTracker activityTracker, GemEventService eventService) {
        this.plugin = plugin;
        this.empConfig = empConfig;
        this.accountService = accountService;
        this.activityTracker = activityTracker;
        this.eventService = eventService;
    }

    public void start() {
        schedulePlaytimeRewards();
        scheduleEventChecks();
    }

    public void stop() {
        if (playtimeTask != null) {
            playtimeTask.cancel();
            playtimeTask = null;
        }
        if (eventCheckTask != null) {
            eventCheckTask.cancel();
            eventCheckTask = null;
        }
    }

    public void handleKill(Player killer, Player victim) {
        FileConfiguration config = empConfig.getConfig();
        if (!config.getBoolean("gems.kills.enabled", true)) {
            return;
        }

        long cooldownMillis = parseDurationMillis(config.getString("gems.kills.cooldown", "5m"));
        boolean blockSameIp = config.getBoolean("gems.kills.block_same_ip", true);

        if (!killRewardGuard.canReward(killer, victim, cooldownMillis, blockSameIp)) {
            return;
        }

        long baseReward = config.getLong("gems.kills.gems_per_kill", 70L);
        long reward = applyMultiplier(baseReward);
        if (reward <= 0) {
            return;
        }

        killRewardGuard.registerKill(killer, victim);

        Scheduler.runTaskAsync(() -> {
            accountService.addBalance(killer.getUniqueId(), killer.getName(), CurrencyType.GEMS, reward,
                    "KILL", killer.getName(), victim.getName());

            if (config.getBoolean("gems.kills.notify", true)) {
                Scheduler.runTask(() -> sendRewardMessage(killer, reward, "emp.gems.kill_reward"));
            }
        });
    }

    private void schedulePlaytimeRewards() {
        FileConfiguration config = empConfig.getConfig();
        long intervalTicks = Math.max(20L, parseDurationTicks(config.getString("gems.playtime.award_interval", "60s")));

        if (playtimeTask != null) {
            playtimeTask.cancel();
        }

        playtimeTask = Scheduler.runTaskTimer(this::rewardPlaytime, intervalTicks, intervalTicks);
    }

    private void scheduleEventChecks() {
        long intervalTicks = 20L * 60L;
        if (eventCheckTask != null) {
            eventCheckTask.cancel();
        }

        eventCheckTask = Scheduler.runTaskTimer(this::checkEventBroadcast, intervalTicks, intervalTicks);
    }

    private void rewardPlaytime() {
        FileConfiguration config = empConfig.getConfig();
        if (!config.getBoolean("gems.playtime.enabled", true)) {
            return;
        }

        long activeWithinMillis = parseDurationMillis(config.getString("gems.playtime.active_within", "2m"));
        long baseReward = config.getLong("gems.playtime.gems_per_minute", 1L);
        long reward = applyMultiplier(baseReward);

        if (reward <= 0) {
            return;
        }

        List<Player> eligible = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (activityTracker.isActive(player.getUniqueId(), activeWithinMillis)) {
                eligible.add(player);
            }
        }

        if (eligible.isEmpty()) {
            return;
        }

        Scheduler.runTaskAsync(() -> {
            for (Player player : eligible) {
                accountService.addBalance(player.getUniqueId(), player.getName(), CurrencyType.GEMS, reward,
                        "PLAYTIME", "Playtime", null);
            }

            if (config.getBoolean("gems.playtime.notify", false)) {
                Scheduler.runTask(() -> {
                    for (Player player : eligible) {
                        sendRewardMessage(player, reward, "emp.gems.playtime_reward");
                    }
                });
            }
        });
    }

    private void checkEventBroadcast() {
        boolean active = eventService.isActive();
        if (active == lastEventActive) {
            return;
        }

        lastEventActive = active;
        String key = active ? "emp.gems.double_start" : "emp.gems.double_end";
        if (empConfig.getConfig().getBoolean("double_gems.announce", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getMessageService().sendMessage(player, key);
            }
            plugin.getMessageService().sendConsoleMessage(key);
        }
    }

    private void sendRewardMessage(Player player, long reward, String key) {
        String formatted = accountService.getGemFormat().format(reward);
        java.util.Map<String, String> placeholders = java.util.Map.of("amount", formatted);
        plugin.getMessageService().sendMessage(player, key, placeholders);
    }

    private long applyMultiplier(long base) {
        double multiplier = eventService.getActiveMultiplier();
        return Math.round(base * multiplier);
    }

    private long parseDurationTicks(String raw) {
        return parseDurationMillis(raw) / 50L;
    }

    private long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return 60000L;
        }

        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        long factor;
        if (trimmed.endsWith("ms")) {
            factor = 1L;
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        } else if (trimmed.endsWith("s")) {
            factor = 1000L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (trimmed.endsWith("m")) {
            factor = 60000L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (trimmed.endsWith("h")) {
            factor = 3600000L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else if (trimmed.endsWith("d")) {
            factor = 86400000L;
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        } else {
            factor = 60000L;
        }

        try {
            long value = Long.parseLong(trimmed);
            return value * factor;
        } catch (NumberFormatException e) {
            return 60000L;
        }
    }
}
