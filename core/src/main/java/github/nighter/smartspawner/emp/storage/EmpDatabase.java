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

    private static final String TPA_REQUESTS_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_tpa_requests (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                requester_uuid VARCHAR(36) NOT NULL,
                requester_name VARCHAR(16) NOT NULL,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(16) NOT NULL,
                expires_at BIGINT NOT NULL,
                status VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_target_uuid (target_uuid),
                INDEX idx_requester_uuid (requester_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String TPA_REQUESTS_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_tpa_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                requester_uuid VARCHAR(36) NOT NULL,
                requester_name VARCHAR(16) NOT NULL,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(16) NOT NULL,
                expires_at BIGINT NOT NULL,
                status VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String TEAM_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_teams (
                team_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                team_name VARCHAR(32) NOT NULL UNIQUE,
                owner_uuid VARCHAR(36) NOT NULL,
                owner_name VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_owner_uuid (owner_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String TEAM_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_teams (
                team_id INTEGER PRIMARY KEY AUTOINCREMENT,
                team_name VARCHAR(32) NOT NULL UNIQUE,
                owner_uuid VARCHAR(36) NOT NULL,
                owner_name VARCHAR(16) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String TEAM_MEMBERS_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_team_members (
                team_id BIGINT NOT NULL,
                member_uuid VARCHAR(36) NOT NULL,
                member_name VARCHAR(16) NOT NULL,
                role VARCHAR(16) NOT NULL,
                joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (team_id, member_uuid),
                INDEX idx_member_uuid (member_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String TEAM_MEMBERS_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_team_members (
                team_id INTEGER NOT NULL,
                member_uuid VARCHAR(36) NOT NULL,
                member_name VARCHAR(16) NOT NULL,
                role VARCHAR(16) NOT NULL,
                joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (team_id, member_uuid)
            )
            """;

    private static final String TEAM_INVITES_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_team_invites (
                team_id BIGINT NOT NULL,
                invited_uuid VARCHAR(36) NOT NULL,
                invited_name VARCHAR(16) NOT NULL,
                inviter_uuid VARCHAR(36) NOT NULL,
                inviter_name VARCHAR(16) NOT NULL,
                expires_at BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (team_id, invited_uuid),
                INDEX idx_invited_uuid (invited_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String TEAM_INVITES_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_team_invites (
                team_id INTEGER NOT NULL,
                invited_uuid VARCHAR(36) NOT NULL,
                invited_name VARCHAR(16) NOT NULL,
                inviter_uuid VARCHAR(36) NOT NULL,
                inviter_name VARCHAR(16) NOT NULL,
                expires_at BIGINT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (team_id, invited_uuid)
            )
            """;

    private static final String BOUNTIES_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_bounties (
                target_uuid VARCHAR(36) PRIMARY KEY,
                target_name VARCHAR(16) NOT NULL,
                bounty_amount BIGINT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String BOUNTIES_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_bounties (
                target_uuid VARCHAR(36) PRIMARY KEY,
                target_name VARCHAR(16) NOT NULL,
                bounty_amount BIGINT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String KILLSTREAK_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_killstreaks (
                uuid VARCHAR(36) PRIMARY KEY,
                last_name VARCHAR(16) NOT NULL,
                current_streak INT NOT NULL DEFAULT 0,
                best_streak INT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String KILLSTREAK_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_killstreaks (
                uuid VARCHAR(36) PRIMARY KEY,
                last_name VARCHAR(16) NOT NULL,
                current_streak INT NOT NULL DEFAULT 0,
                best_streak INT NOT NULL DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String AUCTIONS_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS emp_auctions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                seller_uuid VARCHAR(36) NOT NULL,
                seller_name VARCHAR(16) NOT NULL,
                item_data LONGTEXT NOT NULL,
                price BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                sold TINYINT(1) NOT NULL DEFAULT 0,
                buyer_uuid VARCHAR(36) DEFAULT NULL,
                buyer_name VARCHAR(16) DEFAULT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_active (sold, expires_at),
                INDEX idx_seller (seller_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    private static final String AUCTIONS_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS emp_auctions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                seller_uuid VARCHAR(36) NOT NULL,
                seller_name VARCHAR(16) NOT NULL,
                item_data TEXT NOT NULL,
                price BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                sold INTEGER NOT NULL DEFAULT 0,
                buyer_uuid VARCHAR(36) DEFAULT NULL,
                buyer_name VARCHAR(16) DEFAULT NULL,
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
                stmt.execute(TPA_REQUESTS_TABLE_SQLITE);
                stmt.execute(TEAM_TABLE_SQLITE);
                stmt.execute(TEAM_MEMBERS_TABLE_SQLITE);
                stmt.execute(TEAM_INVITES_TABLE_SQLITE);
                stmt.execute(BOUNTIES_TABLE_SQLITE);
                stmt.execute(KILLSTREAK_TABLE_SQLITE);
                stmt.execute(AUCTIONS_TABLE_SQLITE);
            } else {
                stmt.execute(META_TABLE_MYSQL);
                stmt.execute(ACCOUNTS_TABLE_MYSQL);
                stmt.execute(TRANSACTIONS_TABLE_MYSQL);
                stmt.execute(TPA_REQUESTS_TABLE_MYSQL);
                stmt.execute(TEAM_TABLE_MYSQL);
                stmt.execute(TEAM_MEMBERS_TABLE_MYSQL);
                stmt.execute(TEAM_INVITES_TABLE_MYSQL);
                stmt.execute(BOUNTIES_TABLE_MYSQL);
                stmt.execute(KILLSTREAK_TABLE_MYSQL);
                stmt.execute(AUCTIONS_TABLE_MYSQL);
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
