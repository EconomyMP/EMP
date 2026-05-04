package github.nighter.smartspawner.emp.gems;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.config.EmpConfig;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public class GemEventService {
    private final SmartSpawner plugin;
    private final EmpConfig empConfig;

    private boolean enabled;
    private double multiplier;
    private String mode;

    private ZonedDateTime rangeStart;
    private ZonedDateTime rangeEnd;

    private EnumSet<DayOfWeek> weeklyDays;
    private LocalTime weeklyStart;
    private LocalTime weeklyEnd;
    private ZoneId zoneId;

    public GemEventService(SmartSpawner plugin, EmpConfig empConfig) {
        this.plugin = plugin;
        this.empConfig = empConfig;
        reload();
    }

    public void reload() {
        FileConfiguration config = empConfig.getConfig();
        this.enabled = config.getBoolean("double_gems.enabled", false);
        this.multiplier = config.getDouble("double_gems.multiplier", 2.0);
        this.mode = config.getString("double_gems.mode", "RANGE").toUpperCase(Locale.ROOT);

        String zone = config.getString("double_gems.timezone", "UTC");
        try {
            this.zoneId = ZoneId.of(zone);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid double_gems.timezone: " + zone + ". Falling back to UTC.");
            this.zoneId = ZoneId.of("UTC");
        }

        if ("WEEKLY".equals(mode)) {
            loadWeekly(config);
        } else {
            loadRange(config);
        }
    }

    public boolean isActive() {
        if (!enabled) {
            return false;
        }

        if ("WEEKLY".equals(mode)) {
            return isWeeklyActive();
        }

        return isRangeActive();
    }

    public double getActiveMultiplier() {
        return isActive() ? multiplier : 1.0;
    }

    private void loadRange(FileConfiguration config) {
        String start = config.getString("double_gems.range.start", "2026-05-04T00:00:00");
        String end = config.getString("double_gems.range.end", "2026-05-05T00:00:00");
        try {
            this.rangeStart = LocalDateTime.parse(start).atZone(zoneId);
            this.rangeEnd = LocalDateTime.parse(end).atZone(zoneId);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid double_gems.range values, disabling range mode.");
            this.rangeStart = null;
            this.rangeEnd = null;
        }
    }

    private void loadWeekly(FileConfiguration config) {
        List<String> days = config.getStringList("double_gems.weekly.days");
        this.weeklyDays = EnumSet.noneOf(DayOfWeek.class);
        for (String raw : days) {
            try {
                weeklyDays.add(DayOfWeek.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid double_gems.weekly.days entry: " + raw);
            }
        }

        String start = config.getString("double_gems.weekly.start_time", "00:00");
        String end = config.getString("double_gems.weekly.end_time", "23:59");
        this.weeklyStart = LocalTime.parse(start);
        this.weeklyEnd = LocalTime.parse(end);
    }

    private boolean isRangeActive() {
        if (rangeStart == null || rangeEnd == null) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return !now.isBefore(rangeStart) && !now.isAfter(rangeEnd);
    }

    private boolean isWeeklyActive() {
        if (weeklyDays == null || weeklyDays.isEmpty()) {
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DayOfWeek today = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        if (weeklyStart.equals(weeklyEnd)) {
            return weeklyDays.contains(today);
        }

        if (weeklyStart.isBefore(weeklyEnd)) {
            return weeklyDays.contains(today) && !time.isBefore(weeklyStart) && !time.isAfter(weeklyEnd);
        }

        DayOfWeek previous = today.minus(1);
        boolean lateWindow = weeklyDays.contains(today) && !time.isBefore(weeklyStart);
        boolean earlyWindow = weeklyDays.contains(previous) && !time.isAfter(weeklyEnd);
        return lateWindow || earlyWindow;
    }

}
