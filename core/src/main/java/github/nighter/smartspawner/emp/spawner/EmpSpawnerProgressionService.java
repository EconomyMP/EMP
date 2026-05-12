package github.nighter.smartspawner.emp.spawner;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EmpSpawnerProgressionService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final EmpAccountService accountService;
    private final StorageMode storageMode;
    private final String serverName;

    private final Map<String, SpawnerUpgradeLevels> upgradeCache = new ConcurrentHashMap<>();
    private final Map<String, SpawnerTier> tiersByKey = new HashMap<>();
    private final Map<EntityType, SpawnerTier> tiersByEntity = new HashMap<>();

    public EmpSpawnerProgressionService(SmartSpawner plugin, DatabaseManager databaseManager, EmpAccountService accountService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.accountService = accountService;
        this.storageMode = databaseManager.getStorageMode();
        this.serverName = databaseManager.getServerName();
    }

    public boolean initialize() {
        if (!createTable()) {
            return false;
        }
        reloadConfig();
        return true;
    }

    public void reloadConfig() {
        tiersByKey.clear();
        tiersByEntity.clear();

        FileConfiguration cfg = plugin.getEmpConfig().getConfig();
        ConfigurationSection tiersSection = cfg.getConfigurationSection("spawners.tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("EMP spawner tiers config is missing.");
            return;
        }

        for (String key : tiersSection.getKeys(false)) {
            ConfigurationSection section = tiersSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            EntityType type;
            try {
                type = EntityType.valueOf(section.getString("entity", key).toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid entity for spawner tier " + key);
                continue;
            }

            Material output;
            try {
                output = Material.valueOf(section.getString("output.material", "ROTTEN_FLESH").toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid output material for spawner tier " + key);
                continue;
            }

            String display = section.getString("display_name", key);
            int perMinute = section.getInt("output.per_minute", 2);
            long price = section.getLong("price_gems", 250L);

            int speedMax = section.getInt("upgrades.speed.max_level", 10);
            double speedReduce = section.getDouble("upgrades.speed.delay_reduction_per_level", 0.05);
            String speedCurrency = section.getString("upgrades.speed.currency", "MONEY");
            long[] speedCosts = toCosts(section.getIntegerList("upgrades.speed.costs"));

            int multMax = section.getInt("upgrades.multiplier.max_level", 10);
            double multPer = section.getDouble("upgrades.multiplier.per_level", 0.10);
            String multCurrency = section.getString("upgrades.multiplier.currency", "MONEY");
            long[] multCosts = toCosts(section.getIntegerList("upgrades.multiplier.costs"));

            int effMax = section.getInt("upgrades.efficiency.max_level", 10);
            double effChance = section.getDouble("upgrades.efficiency.chance_bonus_per_level", 2.0);
            String effCurrency = section.getString("upgrades.efficiency.currency", "GEMS");
            long[] effCosts = toCosts(section.getIntegerList("upgrades.efficiency.costs"));

            SpawnerTier tier = new SpawnerTier(
                    key.toUpperCase(Locale.ROOT),
                    display,
                    type,
                    output,
                    perMinute,
                    price,
                    speedMax,
                    speedReduce,
                    multMax,
                    multPer,
                    effMax,
                    effChance,
                    speedCurrency,
                    multCurrency,
                    effCurrency,
                    speedCosts,
                    multCosts,
                    effCosts
            );

            tiersByKey.put(tier.key(), tier);
            tiersByEntity.put(tier.entityType(), tier);
        }
    }

    public Collection<SpawnerTier> getTiers() {
        return Collections.unmodifiableCollection(tiersByKey.values());
    }

    public SpawnerTier getTierByEntity(EntityType type) {
        return tiersByEntity.get(type);
    }

    public SpawnerUpgradeLevels getLevels(String spawnerId) {
        SpawnerUpgradeLevels cached = upgradeCache.get(spawnerId);
        if (cached != null) {
            return cached;
        }

        String sql = "SELECT speed_level, multiplier_level, efficiency_level FROM emp_spawner_upgrades WHERE server_name = ? AND spawner_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serverName);
            stmt.setString(2, spawnerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SpawnerUpgradeLevels levels = new SpawnerUpgradeLevels(
                            rs.getInt("speed_level"),
                            rs.getInt("multiplier_level"),
                            rs.getInt("efficiency_level")
                    );
                    upgradeCache.put(spawnerId, levels);
                    return levels;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load spawner upgrades for " + spawnerId + ": " + e.getMessage());
        }

        upgradeCache.put(spawnerId, SpawnerUpgradeLevels.ZERO);
        return SpawnerUpgradeLevels.ZERO;
    }

    public void invalidate(String spawnerId) {
        upgradeCache.remove(spawnerId);
    }

    public long getAdjustedSpawnDelayMs(SpawnerData spawner, long baseDelayMs) {
        SpawnerTier tier = getTierByEntity(spawner.getEntityType());
        if (tier == null) {
            return baseDelayMs;
        }

        SpawnerUpgradeLevels levels = getLevels(spawner.getSpawnerId());
        double reduction = levels.speedLevel() * tier.speedDelayReductionPerLevel();
        reduction = Math.min(reduction, 0.85);
        long adjusted = Math.round(baseDelayMs * (1.0 - reduction));
        return Math.max(1000L, adjusted);
    }

    public int getOutputPerCycle(SpawnerData spawner) {
        SpawnerTier tier = getTierByEntity(spawner.getEntityType());
        if (tier == null) {
            return 0;
        }

        SpawnerUpgradeLevels levels = getLevels(spawner.getSpawnerId());
        double multiplier = 1.0 + (levels.multiplierLevel() * tier.multiplierPerLevel());

        long baseDelayMs = (spawner.getSpawnDelay() + 20L) * 50L;
        long adjustedDelayMs = getAdjustedSpawnDelayMs(spawner, baseDelayMs);
        double cyclesPerMinute = 60000.0 / adjustedDelayMs;

        double perCycle = (tier.outputPerMinute() * multiplier) / cyclesPerMinute;
        int rounded = (int) Math.max(1, Math.round(perCycle));

        int stack = Math.max(1, spawner.getStackSize());
        return rounded * stack;
    }

    public double getEfficiencyChanceBonus(SpawnerData spawner) {
        SpawnerTier tier = getTierByEntity(spawner.getEntityType());
        if (tier == null) {
            return 0.0;
        }
        SpawnerUpgradeLevels levels = getLevels(spawner.getSpawnerId());
        return levels.efficiencyLevel() * tier.efficiencyChanceBonusPerLevel();
    }

    public UpgradePathResult upgrade(SpawnerData spawner, String path, java.util.UUID playerId, String playerName) {
        SpawnerTier tier = getTierByEntity(spawner.getEntityType());
        if (tier == null) {
            return UpgradePathResult.fail("emp.spawners.upgrade.no_tier", SpawnerUpgradeLevels.ZERO);
        }

        String normalized = path.toLowerCase(Locale.ROOT);
        SpawnerUpgradeLevels before = getLevels(spawner.getSpawnerId());
        SpawnerUpgradeLevels after = before;

        int nextLevel;
        int max;
        String currency;
        long cost;

        switch (normalized) {
            case "speed" -> {
                nextLevel = before.speedLevel() + 1;
                max = tier.speedMax();
                if (nextLevel > max) {
                    return UpgradePathResult.fail("emp.spawners.upgrade.maxed", before);
                }
                currency = tier.speedCurrency();
                cost = resolveCost(tier.speedCosts(), nextLevel);
                after = before.withSpeed(nextLevel);
            }
            case "multiplier" -> {
                nextLevel = before.multiplierLevel() + 1;
                max = tier.multiplierMax();
                if (nextLevel > max) {
                    return UpgradePathResult.fail("emp.spawners.upgrade.maxed", before);
                }
                currency = tier.multiplierCurrency();
                cost = resolveCost(tier.multiplierCosts(), nextLevel);
                after = before.withMultiplier(nextLevel);
            }
            case "efficiency" -> {
                nextLevel = before.efficiencyLevel() + 1;
                max = tier.efficiencyMax();
                if (nextLevel > max) {
                    return UpgradePathResult.fail("emp.spawners.upgrade.maxed", before);
                }
                currency = tier.efficiencyCurrency();
                cost = resolveCost(tier.efficiencyCosts(), nextLevel);
                after = before.withEfficiency(nextLevel);
            }
            default -> {
                return UpgradePathResult.fail("emp.spawners.upgrade.invalid_path", before);
            }
        }

        BalanceChangeResult payment = takeCurrency(playerId, playerName, currency, cost, spawner.getSpawnerId());
        if (!payment.success()) {
            return UpgradePathResult.fail("emp.spawners.upgrade.insufficient", before);
        }

        if (!saveLevels(spawner.getSpawnerId(), after)) {
            refundCurrency(playerId, playerName, currency, cost, spawner.getSpawnerId());
            return UpgradePathResult.fail("emp.spawners.upgrade.persist_failed", before);
        }

        spawner.setCachedSpawnDelay(0L);
        upgradeCache.put(spawner.getSpawnerId(), after);
        return UpgradePathResult.ok(before, after, normalized, cost, currency);
    }

    private BalanceChangeResult takeCurrency(java.util.UUID uuid, String playerName, String currency, long amount, String source) {
        if ("GEMS".equalsIgnoreCase(currency)) {
            return accountService.takeBalance(uuid, playerName, CurrencyType.GEMS, amount, "SPAWNER_UPGRADE", source, null);
        }
        return accountService.takeBalance(uuid, playerName, CurrencyType.MONEY, amount, "SPAWNER_UPGRADE", source, null);
    }

    private void refundCurrency(java.util.UUID uuid, String playerName, String currency, long amount, String source) {
        if ("GEMS".equalsIgnoreCase(currency)) {
            accountService.addBalance(uuid, playerName, CurrencyType.GEMS, amount, "SPAWNER_UPGRADE_REFUND", source, null);
        } else {
            accountService.addBalance(uuid, playerName, CurrencyType.MONEY, amount, "SPAWNER_UPGRADE_REFUND", source, null);
        }
    }

    private long resolveCost(long[] costs, int nextLevel) {
        if (costs.length == 0) {
            return 0L;
        }
        int idx = Math.max(0, Math.min(costs.length - 1, nextLevel - 1));
        return costs[idx];
    }

    private long[] toCosts(java.util.List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return new long[0];
        }
        long[] costs = new long[values.size()];
        for (int i = 0; i < values.size(); i++) {
            costs[i] = values.get(i);
        }
        return costs;
    }

    private boolean saveLevels(String spawnerId, SpawnerUpgradeLevels levels) {
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO emp_spawner_upgrades (server_name, spawner_id, speed_level, multiplier_level, efficiency_level) VALUES (?, ?, ?, ?, ?) ON CONFLICT(server_name, spawner_id) DO UPDATE SET speed_level = excluded.speed_level, multiplier_level = excluded.multiplier_level, efficiency_level = excluded.efficiency_level"
                : "INSERT INTO emp_spawner_upgrades (server_name, spawner_id, speed_level, multiplier_level, efficiency_level) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE speed_level = VALUES(speed_level), multiplier_level = VALUES(multiplier_level), efficiency_level = VALUES(efficiency_level)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serverName);
            stmt.setString(2, spawnerId);
            stmt.setInt(3, levels.speedLevel());
            stmt.setInt(4, levels.multiplierLevel());
            stmt.setInt(5, levels.efficiencyLevel());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save spawner upgrades for " + spawnerId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean createTable() {
        String sql = storageMode == StorageMode.SQLITE
                ? """
                CREATE TABLE IF NOT EXISTS emp_spawner_upgrades (
                    server_name VARCHAR(64) NOT NULL,
                    spawner_id VARCHAR(64) NOT NULL,
                    speed_level INT NOT NULL DEFAULT 0,
                    multiplier_level INT NOT NULL DEFAULT 0,
                    efficiency_level INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (server_name, spawner_id)
                )
                """
                : """
                CREATE TABLE IF NOT EXISTS emp_spawner_upgrades (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    server_name VARCHAR(64) NOT NULL,
                    spawner_id VARCHAR(64) NOT NULL,
                    speed_level INT NOT NULL DEFAULT 0,
                    multiplier_level INT NOT NULL DEFAULT 0,
                    efficiency_level INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_server_spawner (server_name, spawner_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create EMP spawner upgrade table: " + e.getMessage());
            return false;
        }
    }
}
