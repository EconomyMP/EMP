package github.nighter.smartspawner.emp.tpa;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.emp.storage.EmpDatabase;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportRequestService {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final Map<UUID, TpaRequest> requestsByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> requestsByRequester = new ConcurrentHashMap<>();

    public TeleportRequestService(SmartSpawner plugin, EmpDatabase empDatabase, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
    }

    public void loadPendingRequests() {
        Scheduler.runTaskAsync(() -> {
            String sql = "SELECT requester_uuid, requester_name, target_uuid, target_name, expires_at, status FROM emp_tpa_requests WHERE status = 'PENDING'";
            List<TpaRequest> requests = new ArrayList<>();
            long now = System.currentTimeMillis();

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt <= now) {
                        continue;
                    }
                        TpaRequest request = new TpaRequest(
                            UUID.fromString(rs.getString("requester_uuid")),
                            rs.getString("requester_name"),
                            UUID.fromString(rs.getString("target_uuid")),
                            rs.getString("target_name"),
                            expiresAt,
                            rs.getString("status")
                    );
                    requests.add(request);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load pending TPA requests: " + e.getMessage());
            }

            requestsByTarget.clear();
            requestsByRequester.clear();
            for (TpaRequest request : requests) {
                requestsByTarget.put(request.targetUuid(), request);
                requestsByRequester.put(request.requesterUuid(), request);
            }
        });
    }

    public TpaRequest request(Player requester, Player target) {
        cleanupExpired();
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return TpaRequest.selfRequest();
        }
        if (requestsByRequester.containsKey(requester.getUniqueId())) {
            return TpaRequest.alreadyRequested();
        }
        if (requestsByTarget.containsKey(target.getUniqueId())) {
            return TpaRequest.targetBusy();
        }

        TpaRequest request = new TpaRequest(
                requester.getUniqueId(),
                requester.getName(),
                target.getUniqueId(),
                target.getName(),
            System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis(),
            "PENDING"
        );
        requestsByTarget.put(target.getUniqueId(), request);
        requestsByRequester.put(requester.getUniqueId(), request);
        persist(request, "PENDING");
        return request;
    }

    public TpaRequest accept(Player target) {
        cleanupExpired();
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (request == null) {
            return TpaRequest.none();
        }
        requestsByRequester.remove(request.requesterUuid());
        updateStatus(request, "ACCEPTED");
        return request;
    }

    public TpaRequest deny(Player target) {
        cleanupExpired();
        TpaRequest request = requestsByTarget.remove(target.getUniqueId());
        if (request == null) {
            return TpaRequest.none();
        }
        requestsByRequester.remove(request.requesterUuid());
        updateStatus(request, "DENIED");
        return request;
    }

    public TpaRequest cancel(Player requester) {
        cleanupExpired();
        TpaRequest request = requestsByRequester.remove(requester.getUniqueId());
        if (request == null) {
            return TpaRequest.none();
        }
        requestsByTarget.remove(request.targetUuid());
        updateStatus(request, "CANCELLED");
        return request;
    }

    public TpaRequest getIncoming(UUID targetUuid) {
        cleanupExpired();
        return requestsByTarget.get(targetUuid);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        for (TpaRequest request : new ArrayList<>(requestsByTarget.values())) {
            if (request.expiresAt() > now) {
                continue;
            }
            requestsByTarget.remove(request.targetUuid());
            requestsByRequester.remove(request.requesterUuid());
            updateStatus(request, "EXPIRED");
        }
    }

    private void persist(TpaRequest request, String status) {
        Scheduler.runTaskAsync(() -> {
            String sql = storageMode == StorageMode.SQLITE
                    ? "INSERT INTO emp_tpa_requests (requester_uuid, requester_name, target_uuid, target_name, expires_at, status) VALUES (?, ?, ?, ?, ?, ?)"
                    : "INSERT INTO emp_tpa_requests (requester_uuid, requester_name, target_uuid, target_name, expires_at, status) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, request.requesterUuid().toString());
                stmt.setString(2, request.requesterName());
                stmt.setString(3, request.targetUuid().toString());
                stmt.setString(4, request.targetName());
                stmt.setLong(5, request.expiresAt());
                stmt.setString(6, status);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to persist TPA request: " + e.getMessage());
            }
        });
    }

    private void updateStatus(TpaRequest request, String status) {
        Scheduler.runTaskAsync(() -> {
            String sql = "UPDATE emp_tpa_requests SET status = ? WHERE requester_uuid = ? AND target_uuid = ? AND status = 'PENDING'";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status);
                stmt.setString(2, request.requesterUuid().toString());
                stmt.setString(3, request.targetUuid().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update TPA request: " + e.getMessage());
            }
        });
    }

    public record TpaRequest(UUID requesterUuid, String requesterName, UUID targetUuid, String targetName, long expiresAt, String status) {
        public static TpaRequest none() {
            return new TpaRequest(null, null, null, null, 0L, null);
        }

        public static TpaRequest selfRequest() {
            return new TpaRequest(null, null, null, "SELF", 0L, "SELF");
        }

        public static TpaRequest alreadyRequested() {
            return new TpaRequest(null, null, null, "ALREADY", 0L, "ALREADY");
        }

        public static TpaRequest targetBusy() {
            return new TpaRequest(null, null, null, "BUSY", 0L, "BUSY");
        }

        public boolean isEmpty() {
            return status == null;
        }
    }
}
