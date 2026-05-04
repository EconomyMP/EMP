package github.nighter.smartspawner.emp.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class EmpDatabase {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private boolean initialized;

    private static final String META_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_meta (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String META_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_meta (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(64) NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String ACCOUNTS_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_accounts (
                uuid VARCHAR(36) PRIMARY KEY,
                last_name VARCHAR(16) NOT NULL,
                balance BIGINT NOT NULL DEFAULT 0,
                gems BIGINT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String ACCOUNTS_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_accounts (
                uuid VARCHAR(36) PRIMARY KEY,
                last_name VARCHAR(16) NOT NULL,
                balance BIGINT NOT NULL DEFAULT 0,
                gems BIGINT NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String TRANSACTIONS_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_transactions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) DEFAULT NULL,
                currency VARCHAR(16) NOT NULL,
                amount BIGINT NOT NULL,
                balance_before BIGINT NOT NULL,
                balance_after BIGINT NOT NULL,
                reason VARCHAR(64) NOT NULL,
                source VARCHAR(64) DEFAULT NULL,
                target VARCHAR(64) DEFAULT NULL,
                server_name VARCHAR(64) DEFAULT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_uuid (uuid),
                INDEX idx_currency (currency),
                INDEX idx_created (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String TRANSACTIONS_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16) DEFAULT NULL,
                currency VARCHAR(16) NOT NULL,
                amount BIGINT NOT NULL,
                balance_before BIGINT NOT NULL,
                balance_after BIGINT NOT NULL,
                reason VARCHAR(64) NOT NULL,
                source VARCHAR(64) DEFAULT NULL,
                target VARCHAR(64) DEFAULT NULL,
                server_name VARCHAR(64) DEFAULT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    public EmpDatabase(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager != null ? databaseManager.getStorageMode() : null;
    }

    public boolean initialize() {
        if (databaseManager == null || !databaseManager.isActive()) {
            plugin.getLogger().severe("EMP database cannot initialize: database manager is unavailable.");
            return false;
        }

        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            if (storageMode == StorageMode.SQLITE) {
                stmt.execute(META_TABLE_SQLITE);
                stmt.execute(ACCOUNTS_TABLE_SQLITE);
                stmt.execute(TRANSACTIONS_TABLE_SQLITE);
            } else {
                stmt.execute(META_TABLE_MYSQL);
                stmt.execute(ACCOUNTS_TABLE_MYSQL);
                stmt.execute(TRANSACTIONS_TABLE_MYSQL);
            }

            ensureSchemaVersion(conn);
            initialized = true;
            plugin.getLogger().info("EMP database tables initialized.");
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize EMP database tables", e);
            return false;
        }
    }

    private void ensureSchemaVersion(Connection conn) throws SQLException {
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO emp_meta (meta_key, meta_value) VALUES (?, ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
                : "INSERT INTO emp_meta (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, SCHEMA_VERSION_KEY);
            stmt.setString(2, String.valueOf(CURRENT_SCHEMA_VERSION));
            stmt.executeUpdate();
        }
    }

    public boolean isInitialized() {
        return initialized;
    }
}
