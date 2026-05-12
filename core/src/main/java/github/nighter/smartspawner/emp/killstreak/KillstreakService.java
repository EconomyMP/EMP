package github.nighter.smartspawner.emp.killstreak;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class KillstreakService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;

    public KillstreakService(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public KillstreakResult recordKill(Player killer, Player victim) {
        int killerCurrent = getCurrentStreak(killer.getUniqueId());
        int killerBest = getBestStreak(killer.getUniqueId());
        int victimCurrent = getCurrentStreak(victim.getUniqueId());

        int newKillerCurrent = killerCurrent + 1;
        int newKillerBest = Math.max(killerBest, newKillerCurrent);

        upsert(killer.getUniqueId(), killer.getName(), newKillerCurrent, newKillerBest);
        upsert(victim.getUniqueId(), victim.getName(), 0, getBestStreak(victim.getUniqueId()));
        return new KillstreakResult(newKillerCurrent, newKillerBest, victimCurrent);
    }

    public int getCurrentStreak(UUID uuid) {
        String sql = "SELECT current_streak FROM emp_killstreaks WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("current_streak");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load killstreak: " + e.getMessage());
        }
        return 0;
    }

    public int getBestStreak(UUID uuid) {
        String sql = "SELECT best_streak FROM emp_killstreaks WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("best_streak");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load best killstreak: " + e.getMessage());
        }
        return 0;
    }

    public List<KillstreakEntry> getTopStreaks(int limit) {
        List<KillstreakEntry> entries = new ArrayList<>();
        String sql = "SELECT uuid, last_name, current_streak, best_streak FROM emp_killstreaks ORDER BY best_streak DESC, current_streak DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new KillstreakEntry(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("last_name"),
                            rs.getInt("current_streak"),
                            rs.getInt("best_streak")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load killstreak leaderboard: " + e.getMessage());
        }
        entries.sort(Comparator.comparingInt(KillstreakEntry::bestStreak).reversed());
        return entries;
    }

    private void upsert(UUID uuid, String name, int current, int best) {
        String sql = "INSERT INTO emp_killstreaks (uuid, last_name, current_streak, best_streak) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE last_name = VALUES(last_name), current_streak = VALUES(current_streak), best_streak = VALUES(best_streak)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setInt(3, current);
            stmt.setInt(4, best);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save killstreak: " + e.getMessage());
        }
    }

    public record KillstreakEntry(UUID uuid, String name, int currentStreak, int bestStreak) {
    }

    public record KillstreakResult(int killerCurrent, int killerBest, int victimCurrent) {
    }
}
