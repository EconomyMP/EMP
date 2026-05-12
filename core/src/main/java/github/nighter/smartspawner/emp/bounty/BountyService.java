package github.nighter.smartspawner.emp.bounty;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class BountyService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final EmpAccountService accountService;

    public BountyService(SmartSpawner plugin, DatabaseManager databaseManager, EmpAccountService accountService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
        this.accountService = accountService;
    }

    public long getBounty(UUID targetUuid) {
        String sql = "SELECT bounty_amount FROM emp_bounties WHERE target_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("bounty_amount");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load bounty: " + e.getMessage());
        }
        return 0L;
    }

    public BountyResult placeBounty(Player placer, Player target, long amount) {
        if (amount <= 0) {
            return BountyResult.ofFail("invalid_amount");
        }
        if (placer.getUniqueId().equals(target.getUniqueId())) {
            return BountyResult.ofFail("self");
        }

        BalanceChangeResult result = accountService.takeBalance(placer.getUniqueId(), placer.getName(), CurrencyType.MONEY,
                amount, "BOUNTY_PLACE", placer.getName(), target.getName());
        if (!result.success()) {
            return BountyResult.ofFail("insufficient");
        }

        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO emp_bounties (target_uuid, target_name, bounty_amount) VALUES (?, ?, ?) ON CONFLICT(target_uuid) DO UPDATE SET target_name = excluded.target_name, bounty_amount = bounty_amount + excluded.bounty_amount"
                : "INSERT INTO emp_bounties (target_uuid, target_name, bounty_amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE target_name = VALUES(target_name), bounty_amount = bounty_amount + VALUES(bounty_amount)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, target.getUniqueId().toString());
            stmt.setString(2, target.getName());
            stmt.setLong(3, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save bounty: " + e.getMessage());
            accountService.addBalance(placer.getUniqueId(), placer.getName(), CurrencyType.MONEY, amount,
                    "BOUNTY_REFUND", target.getName(), placer.getName());
            return BountyResult.ofFail("invalid");
        }

        return BountyResult.ofSuccess();
    }

    public long claimBounty(Player killer, Player victim) {
        long bounty = getBounty(victim.getUniqueId());
        if (bounty <= 0) {
            return 0L;
        }

        String deleteSql = "DELETE FROM emp_bounties WHERE target_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, victim.getUniqueId().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clear bounty: " + e.getMessage());
        }

        accountService.addBalance(killer.getUniqueId(), killer.getName(), CurrencyType.MONEY, bounty,
                "BOUNTY_CLAIM", victim.getName(), killer.getName());
        return bounty;
    }

    public List<BountyEntry> getTopBounties(int limit) {
        List<BountyEntry> entries = new ArrayList<>();
        String sql = "SELECT target_uuid, target_name, bounty_amount FROM emp_bounties ORDER BY bounty_amount DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new BountyEntry(
                            UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("target_name"),
                            rs.getLong("bounty_amount")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load bounties: " + e.getMessage());
        }
        entries.sort(Comparator.comparingLong(BountyEntry::amount).reversed());
        return entries;
    }

    public record BountyEntry(UUID targetUuid, String targetName, long amount) {
    }

    public record BountyResult(boolean success, String errorKey) {
        public static BountyResult ofSuccess() {
            return new BountyResult(true, null);
        }

        public static BountyResult ofFail(String errorKey) {
            return new BountyResult(false, errorKey);
        }
    }
}
