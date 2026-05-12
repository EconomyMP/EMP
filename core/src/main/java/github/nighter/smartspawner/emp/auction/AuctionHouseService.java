package github.nighter.smartspawner.emp.auction;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.BalanceChangeResult;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuctionHouseService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final EmpAccountService accountService;
    private final Duration defaultDuration;

    public AuctionHouseService(SmartSpawner plugin, DatabaseManager databaseManager, EmpAccountService accountService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
        this.accountService = accountService;
        long hours = plugin.getEmpConfig().getConfig().getLong("auction_house.default_duration_hours", 24L);
        this.defaultDuration = Duration.ofHours(Math.max(1L, hours));
    }

    public void ensureTable() {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSql())) {
            stmt.execute();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to initialize auction table: " + e.getMessage());
        }
    }

    public AuctionResult listHeldItem(Player seller, long price) {
        if (price <= 0) {
            return AuctionResult.fail("invalid_price");
        }

        PlayerInventory inventory = seller.getInventory();
        ItemStack item = inventory.getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return AuctionResult.fail("no_item");
        }

        ItemStack listingItem = item.clone();
        inventory.setItemInMainHand(null);

        try {
            long expiresAt = System.currentTimeMillis() + defaultDuration.toMillis();
            long listingId = insertListing(seller, listingItem, price, expiresAt);
            return AuctionResult.success(listingId, listingItem, price, expiresAt);
        } catch (SQLException e) {
            inventory.setItemInMainHand(listingItem);
            plugin.getLogger().warning("Failed to list auction item: " + e.getMessage());
            return AuctionResult.fail("database_error");
        }
    }

    public Optional<AuctionListing> getListing(long id) {
        String sql = "SELECT * FROM emp_auctions WHERE id = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readListing(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load auction listing: " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<AuctionListing> getListings(int limit) {
        List<AuctionListing> listings = new ArrayList<>();
        String sql = "SELECT * FROM emp_auctions WHERE sold = 0 AND expires_at > ? ORDER BY created_at DESC LIMIT ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    listings.add(readListing(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load auction listings: " + e.getMessage());
        }
        listings.sort(Comparator.comparingLong(AuctionListing::id).reversed());
        return listings;
    }

    public AuctionResult buy(Player buyer, long id) {
        Optional<AuctionListing> optional = getListing(id);
        if (optional.isEmpty()) {
            return AuctionResult.fail("not_found");
        }

        AuctionListing listing = optional.get();
        if (listing.sellerUuid().equals(buyer.getUniqueId())) {
            return AuctionResult.fail("self_buy");
        }
        if (listing.expiresAt() <= System.currentTimeMillis() || listing.sold()) {
            return AuctionResult.fail("expired");
        }

        BalanceChangeResult charge = accountService.takeBalance(buyer.getUniqueId(), buyer.getName(), CurrencyType.MONEY,
                listing.price(), "AH_BUY", listing.sellerName(), buyer.getName());
        if (!charge.success()) {
            return AuctionResult.fail("insufficient");
        }

        boolean marked = markSold(listing.id(), buyer.getUniqueId(), buyer.getName());
        if (!marked) {
            accountService.addBalance(buyer.getUniqueId(), buyer.getName(), CurrencyType.MONEY, listing.price(),
                    "AH_REFUND", listing.sellerName(), buyer.getName());
            return AuctionResult.fail("database_error");
        }

        accountService.addBalance(listing.sellerUuid(), listing.sellerName(), CurrencyType.MONEY, listing.price(),
                "AH_SALE", buyer.getName(), listing.sellerName());

        ItemStack item = listing.item();
        var leftovers = buyer.getInventory().addItem(item);
        leftovers.values().forEach(stack -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), stack));
        return AuctionResult.success(listing.id(), item, listing.price(), listing.expiresAt());
    }

    public AuctionResult cancel(Player seller, long id) {
        Optional<AuctionListing> optional = getListing(id);
        if (optional.isEmpty()) {
            return AuctionResult.fail("not_found");
        }

        AuctionListing listing = optional.get();
        if (!listing.sellerUuid().equals(seller.getUniqueId())) {
            return AuctionResult.fail("not_owner");
        }
        if (listing.sold()) {
            return AuctionResult.fail("sold");
        }

        if (!deleteListing(id)) {
            return AuctionResult.fail("database_error");
        }

        ItemStack item = listing.item();
        var leftovers = seller.getInventory().addItem(item);
        leftovers.values().forEach(stack -> seller.getWorld().dropItemNaturally(seller.getLocation(), stack));
        return AuctionResult.success(listing.id(), item, listing.price(), listing.expiresAt());
    }

    private long insertListing(Player seller, ItemStack item, long price, long expiresAt) throws SQLException {
        String sql = "INSERT INTO emp_auctions (seller_uuid, seller_name, item_data, price, expires_at, sold) VALUES (?, ?, ?, ?, ?, 0)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, seller.getUniqueId().toString());
            stmt.setString(2, seller.getName());
            stmt.setString(3, serialize(item));
            stmt.setLong(4, price);
            stmt.setLong(5, expiresAt);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create auction listing");
    }

    private boolean markSold(long id, UUID buyerUuid, String buyerName) {
        String sql = "UPDATE emp_auctions SET sold = 1, buyer_uuid = ?, buyer_name = ? WHERE id = ? AND sold = 0";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, buyerUuid.toString());
            stmt.setString(2, buyerName);
            stmt.setLong(3, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to mark auction as sold: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteListing(long id) {
        String sql = "DELETE FROM emp_auctions WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete auction listing: " + e.getMessage());
            return false;
        }
    }

    private AuctionListing readListing(ResultSet rs) throws SQLException {
        return new AuctionListing(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                deserialize(rs.getString("item_data")),
                rs.getLong("price"),
                rs.getLong("expires_at"),
                rs.getBoolean("sold"),
                readUuid(rs, "buyer_uuid"),
                rs.getString("buyer_name")
        );
    }

    private UUID readUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private String serialize(ItemStack item) throws SQLException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new SQLException("Failed to serialize auction item", e);
        }
    }

    private ItemStack deserialize(String data) throws SQLException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            Object object = dataInput.readObject();
            return (ItemStack) object;
        } catch (Exception e) {
            throw new SQLException("Failed to deserialize auction item", e);
        }
    }

    private String createTableSql() {
        if (storageMode == StorageMode.SQLITE) {
            return """
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
        }
        return """
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
    }

    public record AuctionListing(long id, UUID sellerUuid, String sellerName, ItemStack item, long price,
                                 long expiresAt, boolean sold, UUID buyerUuid, String buyerName) {
    }

    public record AuctionResult(boolean success, String errorKey, long id, ItemStack item, long price, long expiresAt) {
        public static AuctionResult success(long id, ItemStack item, long price, long expiresAt) {
            return new AuctionResult(true, null, id, item, price, expiresAt);
        }

        public static AuctionResult fail(String errorKey) {
            return new AuctionResult(false, errorKey, -1L, null, 0L, 0L);
        }
    }
}
