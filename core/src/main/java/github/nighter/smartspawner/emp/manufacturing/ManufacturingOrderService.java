package github.nighter.smartspawner.emp.manufacturing;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.economy.CurrencyType;
import github.nighter.smartspawner.emp.economy.EmpAccountService;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ManufacturingOrderService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final EmpAccountService accountService;

    public ManufacturingOrderService(SmartSpawner plugin, DatabaseManager databaseManager, EmpAccountService accountService) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.accountService = accountService;
    }

    public OrderResult placeOrder(Player buyer, UUID manufacturerUuid, String manufacturerName, String itemName, int quantity, long pricePerUnit) {
        if (quantity <= 0 || pricePerUnit <= 0) {
            return OrderResult.fail("invalid_params");
        }

        long totalPrice = pricePerUnit * quantity;
        var charged = accountService.takeBalance(buyer.getUniqueId(), buyer.getName(), CurrencyType.MONEY,
                totalPrice, "MFG_ORDER", manufacturerName, itemName);
        if (!charged.success()) {
            return OrderResult.fail("insufficient_funds");
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO emp_manufacturing_orders (buyer_uuid, buyer_name, manufacturer_uuid, manufacturer_name, item_name, quantity, price_per_unit, total_price, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')")) {
            stmt.setString(1, buyer.getUniqueId().toString());
            stmt.setString(2, buyer.getName());
            stmt.setString(3, manufacturerUuid.toString());
            stmt.setString(4, manufacturerName);
            stmt.setString(5, itemName);
            stmt.setInt(6, quantity);
            stmt.setLong(7, pricePerUnit);
            stmt.setLong(8, totalPrice);
            stmt.executeUpdate();
            return OrderResult.success();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to place order: " + e.getMessage());
            accountService.addBalance(buyer.getUniqueId(), buyer.getName(), CurrencyType.MONEY, totalPrice,
                    "MFG_ORDER_REFUND", manufacturerName, itemName);
            return OrderResult.fail("database_error");
        }
    }

    public List<ManufacturingOrder> getBuyerOrders(UUID buyerUuid) {
        List<ManufacturingOrder> orders = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, buyer_uuid, buyer_name, manufacturer_uuid, manufacturer_name, item_name, quantity, price_per_unit, total_price, status FROM emp_manufacturing_orders WHERE buyer_uuid = ? ORDER BY created_at DESC")) {
            stmt.setString(1, buyerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(readOrder(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load buyer orders: " + e.getMessage());
        }
        return orders;
    }

    public List<ManufacturingOrder> getManufacturerOrders(UUID manufacturerUuid) {
        List<ManufacturingOrder> orders = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, buyer_uuid, buyer_name, manufacturer_uuid, manufacturer_name, item_name, quantity, price_per_unit, total_price, status FROM emp_manufacturing_orders WHERE manufacturer_uuid = ? ORDER BY created_at DESC")) {
            stmt.setString(1, manufacturerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(readOrder(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load manufacturer orders: " + e.getMessage());
        }
        return orders;
    }

    public OrderResult completeOrder(UUID manufacturerUuid, long orderId) {
        Optional<ManufacturingOrder> optional = getOrder(orderId);
        if (optional.isEmpty()) {
            return OrderResult.fail("not_found");
        }

        ManufacturingOrder order = optional.get();
        if (!order.manufacturerUuid().equals(manufacturerUuid)) {
            return OrderResult.fail("not_owner");
        }
        if (!"PENDING".equalsIgnoreCase(order.status())) {
            return OrderResult.fail("already_completed");
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE emp_manufacturing_orders SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            stmt.setLong(1, orderId);
            stmt.executeUpdate();

            accountService.addBalance(manufacturerUuid, order.manufacturerName(), CurrencyType.MONEY, order.totalPrice(),
                    "MFG_PAYMENT", order.buyerName(), order.itemName());
            return OrderResult.success();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to complete order: " + e.getMessage());
            return OrderResult.fail("database_error");
        }
    }

    public Optional<ManufacturingOrder> getOrder(long orderId) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id, buyer_uuid, buyer_name, manufacturer_uuid, manufacturer_name, item_name, quantity, price_per_unit, total_price, status FROM emp_manufacturing_orders WHERE id = ?")) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readOrder(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load order: " + e.getMessage());
        }
        return Optional.empty();
    }

    private ManufacturingOrder readOrder(ResultSet rs) throws SQLException {
        return new ManufacturingOrder(
                rs.getLong("id"),
                UUID.fromString(rs.getString("buyer_uuid")),
                rs.getString("buyer_name"),
                UUID.fromString(rs.getString("manufacturer_uuid")),
                rs.getString("manufacturer_name"),
                rs.getString("item_name"),
                rs.getInt("quantity"),
                rs.getLong("price_per_unit"),
                rs.getLong("total_price"),
                rs.getString("status")
        );
    }

    public record ManufacturingOrder(long id, UUID buyerUuid, String buyerName, UUID manufacturerUuid, String manufacturerName, String itemName, int quantity, long pricePerUnit, long totalPrice, String status) {}
    public record OrderResult(boolean success, String errorKey) {
        public static OrderResult success() { return new OrderResult(true, null); }
        public static OrderResult fail(String errorKey) { return new OrderResult(false, errorKey); }
    }
}
