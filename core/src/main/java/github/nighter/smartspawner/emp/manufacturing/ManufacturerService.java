package github.nighter.smartspawner.emp.manufacturing;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ManufacturerService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final EmpAccountService accountService;

    public ManufacturerService(SmartSpawner plugin, DatabaseManager databaseManager, EmpAccountService accountService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
        this.accountService = accountService;
    }

    public void ensureTables() {
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            if (storageMode == StorageMode.SQLITE) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturers (
                        uuid VARCHAR(36) PRIMARY KEY,
                        shop_name VARCHAR(32) NOT NULL UNIQUE,
                        owner_name VARCHAR(16) NOT NULL,
                        registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_sales BIGINT DEFAULT 0,
                        avg_rating REAL DEFAULT 0.0,
                        rating_count INT DEFAULT 0
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturing_listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        item_name VARCHAR(64) NOT NULL,
                        price BIGINT NOT NULL,
                        quantity INT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturing_orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        buyer_uuid VARCHAR(36) NOT NULL,
                        buyer_name VARCHAR(16) NOT NULL,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        manufacturer_name VARCHAR(16) NOT NULL,
                        item_name VARCHAR(64) NOT NULL,
                        quantity INT NOT NULL,
                        price_per_unit BIGINT NOT NULL,
                        total_price BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        completed_at TIMESTAMP DEFAULT NULL
                    )
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturer_ratings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        rater_uuid VARCHAR(36) NOT NULL,
                        rater_name VARCHAR(16) NOT NULL,
                        stars INT NOT NULL,
                        comment TEXT DEFAULT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            } else {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturers (
                        uuid VARCHAR(36) PRIMARY KEY,
                        shop_name VARCHAR(32) NOT NULL UNIQUE,
                        owner_name VARCHAR(16) NOT NULL,
                        registration_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_sales BIGINT DEFAULT 0,
                        avg_rating REAL DEFAULT 0.0,
                        rating_count INT DEFAULT 0
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturing_listings (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        item_name VARCHAR(64) NOT NULL,
                        price BIGINT NOT NULL,
                        quantity INT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturing_orders (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        buyer_uuid VARCHAR(36) NOT NULL,
                        buyer_name VARCHAR(16) NOT NULL,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        manufacturer_name VARCHAR(16) NOT NULL,
                        item_name VARCHAR(64) NOT NULL,
                        quantity INT NOT NULL,
                        price_per_unit BIGINT NOT NULL,
                        total_price BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        completed_at TIMESTAMP DEFAULT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS emp_manufacturer_ratings (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        manufacturer_uuid VARCHAR(36) NOT NULL,
                        rater_uuid VARCHAR(36) NOT NULL,
                        rater_name VARCHAR(16) NOT NULL,
                        stars INT NOT NULL,
                        comment TEXT DEFAULT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create manufacturing tables: " + e.getMessage());
        }
    }

    public ManufacturerResult register(Player owner, String shopName) {
        if (shopName == null || shopName.isBlank() || shopName.length() > 32) {
            return ManufacturerResult.fail("invalid_name");
        }
        if (isManufacturer(owner.getUniqueId())) {
            return ManufacturerResult.fail("already_registered");
        }

        long regFee = plugin.getEmpConfig().getConfig().getLong("manufacturing.registration_fee", 10000L);
        var charged = accountService.takeBalance(owner.getUniqueId(), owner.getName(), CurrencyType.MONEY,
                regFee, "MFG_REGISTER", "Manufacturing", shopName);
        if (!charged.success()) {
            return ManufacturerResult.fail("insufficient_funds");
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO emp_manufacturers (uuid, shop_name, owner_name) VALUES (?, ?, ?)")) {
            stmt.setString(1, owner.getUniqueId().toString());
            stmt.setString(2, shopName.trim());
            stmt.setString(3, owner.getName());
            stmt.executeUpdate();
            return ManufacturerResult.success(new Manufacturer(owner.getUniqueId(), shopName.trim(), owner.getName()));
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to register manufacturer: " + e.getMessage());
            accountService.addBalance(owner.getUniqueId(), owner.getName(), CurrencyType.MONEY, regFee,
                    "MFG_REGISTER_REFUND", "Manufacturing", shopName);
            return ManufacturerResult.fail("database_error");
        }
    }

    public boolean isManufacturer(UUID uuid) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM emp_manufacturers WHERE uuid = ? LIMIT 1")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public Optional<Manufacturer> getManufacturer(UUID uuid) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, shop_name, owner_name, total_sales, avg_rating, rating_count FROM emp_manufacturers WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Manufacturer(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("shop_name"),
                            rs.getString("owner_name")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load manufacturer: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<Manufacturer> findByShopName(String shopName) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, shop_name, owner_name FROM emp_manufacturers WHERE LOWER(shop_name) = LOWER(?) LIMIT 1")) {
            stmt.setString(1, shopName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Manufacturer(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("shop_name"),
                            rs.getString("owner_name")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to find manufacturer by shop name: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<ManufacturerListing> searchListings(String itemName) {
        List<ManufacturerListing> listings = new ArrayList<>();
        String sql = "SELECT id, manufacturer_uuid, item_name, price, quantity FROM emp_manufacturing_listings WHERE LOWER(item_name) LIKE LOWER(?) AND quantity > 0 ORDER BY price ASC LIMIT 50";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + itemName + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    listings.add(new ManufacturerListing(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("manufacturer_uuid")),
                            rs.getString("item_name"),
                            rs.getLong("price"),
                            rs.getInt("quantity")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to search listings: " + e.getMessage());
        }
        return listings;
    }

    public boolean addListing(UUID manufacturerUuid, String itemName, long price, int quantity) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO emp_manufacturing_listings (manufacturer_uuid, item_name, price, quantity) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, manufacturerUuid.toString());
            stmt.setString(2, itemName);
            stmt.setLong(3, price);
            stmt.setInt(4, quantity);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add listing: " + e.getMessage());
            return false;
        }
    }

    public List<ManufacturerListing> getInventory(UUID manufacturerUuid) {
        List<ManufacturerListing> listings = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, manufacturer_uuid, item_name, price, quantity FROM emp_manufacturing_listings WHERE manufacturer_uuid = ? ORDER BY item_name ASC")) {
            stmt.setString(1, manufacturerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    listings.add(new ManufacturerListing(
                            rs.getLong("id"),
                            manufacturerUuid,
                            rs.getString("item_name"),
                            rs.getLong("price"),
                            rs.getInt("quantity")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load inventory: " + e.getMessage());
        }
        return listings;
    }

    public record Manufacturer(UUID uuid, String shopName, String ownerName) {}
    public record ManufacturerListing(long id, UUID manufacturerUuid, String itemName, long price, int quantity) {}
    public record ManufacturerResult(boolean success, String errorKey, Manufacturer manufacturer) {
        public static ManufacturerResult success(Manufacturer manufacturer) {
            return new ManufacturerResult(true, null, manufacturer);
        }
        public static ManufacturerResult fail(String errorKey) {
            return new ManufacturerResult(false, errorKey, null);
        }
    }
}
