package github.nighter.smartspawner.emp.economy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class EmpTransactionLogger {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final boolean enabled;
    private final String serverName;

    public EmpTransactionLogger(SmartSpawner plugin, DatabaseManager databaseManager, boolean enabled) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.enabled = enabled;
        this.serverName = databaseManager != null ? databaseManager.getServerName() : null;
    }

    public void log(Connection conn, UUID uuid, String name, CurrencyType currency, long amount,
                    long before, long after, String reason, String source, String target) throws SQLException {
        if (!enabled) {
            return;
        }

        String sql = """
                INSERT INTO emp_transactions (
                    uuid, player_name, currency, amount, balance_before, balance_after,
                    reason, source, target, server_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, currency.name());
            stmt.setLong(4, amount);
            stmt.setLong(5, before);
            stmt.setLong(6, after);
            stmt.setString(7, reason);
            stmt.setString(8, source);
            stmt.setString(9, target);
            stmt.setString(10, serverName);
            stmt.executeUpdate();
        }
    }
}
