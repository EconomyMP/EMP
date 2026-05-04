package github.nighter.smartspawner.emp.economy;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.config.EmpConfig;
import github.nighter.smartspawner.emp.util.StripedLock;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class EmpAccountService {
    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final EmpTransactionLogger transactionLogger;
    private final StripedLock locks = new StripedLock(64);

    private final CurrencyFormat moneyFormat;
    private final CurrencyFormat gemFormat;
    private final long startingBalance;
    private final long startingGems;

    public EmpAccountService(SmartSpawner plugin, EmpConfig empConfig, DatabaseManager databaseManager,
                             EmpTransactionLogger transactionLogger) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
        this.transactionLogger = transactionLogger;

        FileConfiguration config = empConfig.getConfig();
        this.moneyFormat = loadMoneyFormat(config);
        this.gemFormat = loadGemFormat(config);
        this.startingBalance = toMinorUnits(config.getDouble("economy.starting_balance", 0.0), moneyFormat);
        this.startingGems = toMinorUnits(config.getDouble("gems.starting_balance", 0.0), gemFormat);
    }

    public CurrencyFormat getMoneyFormat() {
        return moneyFormat;
    }

    public CurrencyFormat getGemFormat() {
        return gemFormat;
    }

    public void ensureAccountAsync(UUID uuid, String name) {
        Scheduler.runTaskAsync(() -> ensureAccount(uuid, name));
    }

    public void ensureAccount(UUID uuid, String name) {
        ReentrantLock lock = locks.lockFor(uuid);
        lock.lock();
        try (Connection conn = databaseManager.getConnection()) {
            ensureAccountInternal(conn, uuid, name);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to ensure EMP account: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public long getBalance(UUID uuid, CurrencyType currency) {
        ReentrantLock lock = locks.lockFor(uuid);
        lock.lock();
        try (Connection conn = databaseManager.getConnection()) {
            ensureAccountInternal(conn, uuid, null);
            return selectBalance(conn, uuid, currency);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load EMP balance: " + e.getMessage());
            return 0L;
        } finally {
            lock.unlock();
        }
    }

    public BalanceChangeResult addBalance(UUID uuid, String name, CurrencyType currency, long amount,
                                          String reason, String source, String target) {
        return changeBalance(uuid, name, currency, amount, reason, source, target, false);
    }

    public BalanceChangeResult takeBalance(UUID uuid, String name, CurrencyType currency, long amount,
                                           String reason, String source, String target) {
        return changeBalance(uuid, name, currency, -amount, reason, source, target, false);
    }

    public BalanceChangeResult setBalance(UUID uuid, String name, CurrencyType currency, long amount,
                                          String reason, String source, String target) {
        return setBalanceInternal(uuid, name, currency, amount, reason, source, target);
    }

    public BalanceChangeResult transfer(UUID from, String fromName, UUID to, String toName,
                                        CurrencyType currency, long amount, String reason) {
        if (amount <= 0) {
            return BalanceChangeResult.failure("invalid_amount", 0L);
        }

        UUID first = from.compareTo(to) <= 0 ? from : to;
        UUID second = from.compareTo(to) <= 0 ? to : from;
        ReentrantLock lock1 = locks.lockFor(first);
        ReentrantLock lock2 = locks.lockFor(second);

        if (lock1 == lock2) {
            lock1.lock();
        } else {
            lock1.lock();
            lock2.lock();
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            ensureAccountInternal(conn, from, fromName);
            ensureAccountInternal(conn, to, toName);

            long fromBalance = selectBalanceForUpdate(conn, from, currency);
            if (fromBalance < amount) {
                conn.rollback();
                return BalanceChangeResult.failure("insufficient_funds", fromBalance);
            }

            long toBalance = selectBalanceForUpdate(conn, to, currency);

            long fromAfter = fromBalance - amount;
            long toAfter = toBalance + amount;

            updateBalance(conn, from, fromName, currency, fromAfter);
            updateBalance(conn, to, toName, currency, toAfter);

            transactionLogger.log(conn, from, fromName, currency, -amount, fromBalance, fromAfter,
                    reason, fromName, toName);
            transactionLogger.log(conn, to, toName, currency, amount, toBalance, toAfter,
                    reason, fromName, toName);

            conn.commit();
            return BalanceChangeResult.success(fromBalance, fromAfter);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to transfer EMP balance: " + e.getMessage());
            return BalanceChangeResult.failure("database_error", 0L);
        } finally {
            if (lock1 == lock2) {
                lock1.unlock();
            } else {
                lock2.unlock();
                lock1.unlock();
            }
        }
    }

    public List<BalanceEntry> getTopBalances(int limit, int offset) {
        List<BalanceEntry> results = new ArrayList<>();
        String sql = "SELECT uuid, last_name, balance FROM emp_accounts ORDER BY balance DESC LIMIT ? OFFSET ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("last_name");
                    long balance = rs.getLong("balance");
                    results.add(new BalanceEntry(uuid, name, balance));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load balance top list: " + e.getMessage());
        }

        return results;
    }

    public int getAccountCount() {
        String sql = "SELECT COUNT(*) AS total FROM emp_accounts";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load account count: " + e.getMessage());
        }
        return 0;
    }

    private BalanceChangeResult changeBalance(UUID uuid, String name, CurrencyType currency, long delta,
                                              String reason, String source, String target, boolean allowNegative) {
        ReentrantLock lock = locks.lockFor(uuid);
        lock.lock();
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            ensureAccountInternal(conn, uuid, name);
            long current = selectBalanceForUpdate(conn, uuid, currency);
            long updated = current + delta;

            if (!allowNegative && updated < 0) {
                conn.rollback();
                return BalanceChangeResult.failure("insufficient_funds", current);
            }

            updateBalance(conn, uuid, name, currency, updated);
            transactionLogger.log(conn, uuid, name, currency, delta, current, updated, reason, source, target);
            conn.commit();
            return BalanceChangeResult.success(current, updated);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update EMP balance: " + e.getMessage());
            return BalanceChangeResult.failure("database_error", 0L);
        } finally {
            lock.unlock();
        }
    }

    private BalanceChangeResult setBalanceInternal(UUID uuid, String name, CurrencyType currency, long amount,
                                                   String reason, String source, String target) {
        ReentrantLock lock = locks.lockFor(uuid);
        lock.lock();
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            ensureAccountInternal(conn, uuid, name);
            long current = selectBalanceForUpdate(conn, uuid, currency);
            updateBalance(conn, uuid, name, currency, amount);
            transactionLogger.log(conn, uuid, name, currency, amount - current, current, amount, reason, source, target);
            conn.commit();
            return BalanceChangeResult.success(current, amount);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set EMP balance: " + e.getMessage());
            return BalanceChangeResult.failure("database_error", 0L);
        } finally {
            lock.unlock();
        }
    }

    private void ensureAccountInternal(Connection conn, UUID uuid, String name) throws SQLException {
        String playerName = name == null ? "unknown" : name;
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO emp_accounts (uuid, last_name, balance, gems) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET last_name = excluded.last_name"
                : "INSERT INTO emp_accounts (uuid, last_name, balance, gems) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE last_name = VALUES(last_name)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, startingBalance);
            stmt.setLong(4, startingGems);
            stmt.executeUpdate();
        }
    }

    private long selectBalance(Connection conn, UUID uuid, CurrencyType currency) throws SQLException {
        String column = currency == CurrencyType.MONEY ? "balance" : "gems";
        String sql = "SELECT " + column + " FROM emp_accounts WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(column);
                }
            }
        }
        return 0L;
    }

    private long selectBalanceForUpdate(Connection conn, UUID uuid, CurrencyType currency) throws SQLException {
        String column = currency == CurrencyType.MONEY ? "balance" : "gems";
        String sql = storageMode == StorageMode.MYSQL
                ? "SELECT " + column + " FROM emp_accounts WHERE uuid = ? FOR UPDATE"
                : "SELECT " + column + " FROM emp_accounts WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(column);
                }
            }
        }

        return 0L;
    }

    private void updateBalance(Connection conn, UUID uuid, String name, CurrencyType currency, long newBalance)
            throws SQLException {
        String column = currency == CurrencyType.MONEY ? "balance" : "gems";
        String sql = "UPDATE emp_accounts SET " + column + " = ?, last_name = ? WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, newBalance);
            stmt.setString(2, name == null ? "unknown" : name);
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        }
    }

    private CurrencyFormat loadMoneyFormat(FileConfiguration config) {
        String symbol = config.getString("economy.currency.symbol", "$");
        String format = config.getString("economy.currency.format", "{symbol}{amount}");
        int decimals = config.getInt("economy.currency.decimals", 2);
        char thousands = getSeparator(config.getString("economy.currency.thousands_separator", ","), ',');
        char decimal = getSeparator(config.getString("economy.currency.decimal_separator", "."), '.');
        return new CurrencyFormat(symbol, format, decimals, thousands, decimal);
    }

    private CurrencyFormat loadGemFormat(FileConfiguration config) {
        String symbol = config.getString("gems.currency.symbol", "G");
        String format = config.getString("gems.currency.format", "{amount} {symbol}");
        int decimals = config.getInt("gems.currency.decimals", 0);
        char thousands = getSeparator(config.getString("gems.currency.thousands_separator", ","), ',');
        char decimal = getSeparator(config.getString("gems.currency.decimal_separator", "."), '.');
        return new CurrencyFormat(symbol, format, decimals, thousands, decimal);
    }

    private char getSeparator(String raw, char fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        return raw.charAt(0);
    }

    private long toMinorUnits(double amount, CurrencyFormat format) {
        BigDecimal value = BigDecimal.valueOf(amount);
        value = value.setScale(format.getDecimals(), RoundingMode.HALF_UP);
        return value.movePointRight(format.getDecimals()).longValueExact();
    }
}
