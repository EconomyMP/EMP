package github.nighter.smartspawner.emp.team;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.database.DatabaseManager;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TeamService {
    public static final Duration INVITE_TIMEOUT = Duration.ofMinutes(10);

    private final SmartSpawner plugin;
    private final DatabaseManager databaseManager;
    private final StorageMode storageMode;
    private final ConcurrentMap<UUID, TeamMembership> membershipCache = new ConcurrentHashMap<>();

    public TeamService(SmartSpawner plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.storageMode = databaseManager.getStorageMode();
    }

    public void warmCache() {
        Scheduler.runTaskAsync(() -> {
            String sql = "SELECT tm.member_uuid, tm.member_name, tm.role, t.team_id, t.team_name, t.owner_uuid, t.owner_name FROM emp_team_members tm INNER JOIN emp_teams t ON t.team_id = tm.team_id";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                membershipCache.clear();
                while (rs.next()) {
                    TeamMembership membership = new TeamMembership(
                            rs.getLong("team_id"),
                            rs.getString("team_name"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("owner_name"),
                            UUID.fromString(rs.getString("member_uuid")),
                            rs.getString("member_name"),
                            rs.getString("role")
                    );
                    membershipCache.put(membership.memberUuid(), membership);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to warm team cache: " + e.getMessage());
            }
        });
    }

    public Optional<TeamMembership> getMembership(UUID memberUuid) {
        TeamMembership cached = membershipCache.get(memberUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        return loadMembership(memberUuid);
    }

    public TeamResult createTeam(Player owner, String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return TeamResult.fail("invalid_name");
        }
        if (getMembership(owner.getUniqueId()).isPresent()) {
            return TeamResult.fail("already_member");
        }

        String normalized = teamName.trim();
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            long teamId = insertTeam(conn, normalized, owner);
            insertMember(conn, teamId, owner.getUniqueId(), owner.getName(), "OWNER");
            conn.commit();

            TeamMembership membership = new TeamMembership(teamId, normalized, owner.getUniqueId(), owner.getName(), owner.getUniqueId(), owner.getName(), "OWNER");
            membershipCache.put(owner.getUniqueId(), membership);
            if (plugin.getChatPresentationService() != null) {
                plugin.getChatPresentationService().refreshOnlinePlayers();
            }
            return TeamResult.success(membership);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create team: " + e.getMessage());
            return TeamResult.fail("database_error");
        }
    }

    public TeamResult invite(Player inviter, Player invited) {
        Optional<TeamMembership> membership = getMembership(inviter.getUniqueId());
        if (membership.isEmpty()) {
            return TeamResult.fail("not_in_team");
        }
        if (getMembership(invited.getUniqueId()).isPresent()) {
            return TeamResult.fail("target_in_team");
        }

        TeamMembership team = membership.get();
        long expiresAt = System.currentTimeMillis() + INVITE_TIMEOUT.toMillis();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(storageMode == StorageMode.SQLITE
                     ? "INSERT INTO emp_team_invites (team_id, invited_uuid, invited_name, inviter_uuid, inviter_name, expires_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(team_id, invited_uuid) DO UPDATE SET inviter_uuid = excluded.inviter_uuid, inviter_name = excluded.inviter_name, invited_name = excluded.invited_name, expires_at = excluded.expires_at"
                     : "INSERT INTO emp_team_invites (team_id, invited_uuid, invited_name, inviter_uuid, inviter_name, expires_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE inviter_uuid = VALUES(inviter_uuid), inviter_name = VALUES(inviter_name), invited_name = VALUES(invited_name), expires_at = VALUES(expires_at)")) {
            stmt.setLong(1, team.teamId());
            stmt.setString(2, invited.getUniqueId().toString());
            stmt.setString(3, invited.getName());
            stmt.setString(4, inviter.getUniqueId().toString());
            stmt.setString(5, inviter.getName());
            stmt.setLong(6, expiresAt);
            stmt.executeUpdate();
            return TeamResult.success(team);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save team invite: " + e.getMessage());
            return TeamResult.fail("database_error");
        }
    }

    public TeamResult acceptInvite(Player invited) {
        TeamInvite invite = loadInvite(invited.getUniqueId()).orElse(null);
        if (invite == null) {
            return TeamResult.fail("no_invite");
        }
        if (getMembership(invited.getUniqueId()).isPresent()) {
            return TeamResult.fail("already_member");
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            insertMember(conn, invite.teamId(), invited.getUniqueId(), invited.getName(), "MEMBER");
            deleteInvite(conn, invite.teamId(), invited.getUniqueId());
            conn.commit();

            TeamMembership membership = new TeamMembership(invite.teamId(), invite.teamName(), invite.inviterUuid(), invite.inviterName(), invited.getUniqueId(), invited.getName(), "MEMBER");
            membershipCache.put(invited.getUniqueId(), membership);
            if (plugin.getChatPresentationService() != null) {
                plugin.getChatPresentationService().refreshOnlinePlayers();
            }
            return TeamResult.success(membership);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to accept team invite: " + e.getMessage());
            return TeamResult.fail("database_error");
        }
    }

    public TeamResult leave(Player player) {
        Optional<TeamMembership> membership = getMembership(player.getUniqueId());
        if (membership.isEmpty()) {
            return TeamResult.fail("not_in_team");
        }
        TeamMembership current = membership.get();
        if ("OWNER".equalsIgnoreCase(current.role())) {
            return TeamResult.fail("owner_cannot_leave");
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM emp_team_members WHERE team_id = ? AND member_uuid = ?")) {
            stmt.setLong(1, current.teamId());
            stmt.setString(2, player.getUniqueId().toString());
            stmt.executeUpdate();
            membershipCache.remove(player.getUniqueId());
            if (plugin.getChatPresentationService() != null) {
                plugin.getChatPresentationService().refreshOnlinePlayers();
            }
            return TeamResult.success(current);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to leave team: " + e.getMessage());
            return TeamResult.fail("database_error");
        }
    }

    public TeamResult disband(Player owner) {
        Optional<TeamMembership> membership = getMembership(owner.getUniqueId());
        if (membership.isEmpty()) {
            return TeamResult.fail("not_in_team");
        }
        TeamMembership current = membership.get();
        if (!"OWNER".equalsIgnoreCase(current.role())) {
            return TeamResult.fail("not_owner");
        }

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteMembers = conn.prepareStatement("DELETE FROM emp_team_members WHERE team_id = ?");
                 PreparedStatement deleteInvites = conn.prepareStatement("DELETE FROM emp_team_invites WHERE team_id = ?");
                 PreparedStatement deleteTeam = conn.prepareStatement("DELETE FROM emp_teams WHERE team_id = ?")) {
                deleteMembers.setLong(1, current.teamId());
                deleteMembers.executeUpdate();
                deleteInvites.setLong(1, current.teamId());
                deleteInvites.executeUpdate();
                deleteTeam.setLong(1, current.teamId());
                deleteTeam.executeUpdate();
            }
            membershipCache.entrySet().removeIf(entry -> entry.getValue().teamId() == current.teamId());
            conn.commit();
            if (plugin.getChatPresentationService() != null) {
                plugin.getChatPresentationService().refreshOnlinePlayers();
            }
            return TeamResult.success(current);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to disband team: " + e.getMessage());
            return TeamResult.fail("database_error");
        }
    }

    public List<TeamMembership> getTeamMembers(UUID memberUuid) {
        Optional<TeamMembership> membership = getMembership(memberUuid);
        if (membership.isEmpty()) {
            return Collections.emptyList();
        }
        long teamId = membership.get().teamId();
        List<TeamMembership> members = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT tm.member_uuid, tm.member_name, tm.role, t.team_id, t.team_name, t.owner_uuid, t.owner_name FROM emp_team_members tm INNER JOIN emp_teams t ON t.team_id = tm.team_id WHERE tm.team_id = ? ORDER BY tm.role DESC, tm.member_name ASC")) {
            stmt.setLong(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    members.add(new TeamMembership(
                            rs.getLong("team_id"),
                            rs.getString("team_name"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("owner_name"),
                            UUID.fromString(rs.getString("member_uuid")),
                            rs.getString("member_name"),
                            rs.getString("role")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load team members: " + e.getMessage());
        }
        return members;
    }

    public Optional<TeamInvite> getInvite(UUID invitedUuid) {
        return loadInvite(invitedUuid);
    }

    private Optional<TeamMembership> loadMembership(UUID memberUuid) {
        String sql = "SELECT tm.member_uuid, tm.member_name, tm.role, t.team_id, t.team_name, t.owner_uuid, t.owner_name FROM emp_team_members tm INNER JOIN emp_teams t ON t.team_id = tm.team_id WHERE tm.member_uuid = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memberUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    TeamMembership membership = new TeamMembership(
                            rs.getLong("team_id"),
                            rs.getString("team_name"),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("owner_name"),
                            UUID.fromString(rs.getString("member_uuid")),
                            rs.getString("member_name"),
                            rs.getString("role")
                    );
                    membershipCache.put(memberUuid, membership);
                    return Optional.of(membership);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load team membership: " + e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<TeamInvite> loadInvite(UUID invitedUuid) {
        String sql = "SELECT team_id, invited_uuid, invited_name, inviter_uuid, inviter_name, expires_at FROM emp_team_invites WHERE invited_uuid = ? ORDER BY created_at DESC LIMIT 1";
        long now = System.currentTimeMillis();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, invitedUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long expiresAt = rs.getLong("expires_at");
                    if (expiresAt <= now) {
                        return Optional.empty();
                    }
                    return Optional.of(new TeamInvite(
                            rs.getLong("team_id"),
                            rs.getString("invited_uuid"),
                            rs.getString("invited_name"),
                            UUID.fromString(rs.getString("inviter_uuid")),
                            rs.getString("inviter_name"),
                            expiresAt,
                            loadTeamName(rs.getLong("team_id"))
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load team invite: " + e.getMessage());
        }
        return Optional.empty();
    }

    private String loadTeamName(long teamId) throws SQLException {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT team_name, owner_uuid, owner_name FROM emp_teams WHERE team_id = ? LIMIT 1")) {
            stmt.setLong(1, teamId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("team_name");
                }
            }
        }
        return "Team";
    }

    private long insertTeam(Connection conn, String teamName, Player owner) throws SQLException {
        String sql = storageMode == StorageMode.SQLITE
                ? "INSERT INTO emp_teams (team_name, owner_uuid, owner_name) VALUES (?, ?, ?)"
                : "INSERT INTO emp_teams (team_name, owner_uuid, owner_name) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, teamName);
            stmt.setString(2, owner.getUniqueId().toString());
            stmt.setString(3, owner.getName());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve team id");
    }

    private void insertMember(Connection conn, long teamId, UUID memberUuid, String memberName, String role) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO emp_team_members (team_id, member_uuid, member_name, role) VALUES (?, ?, ?, ?)") ) {
            stmt.setLong(1, teamId);
            stmt.setString(2, memberUuid.toString());
            stmt.setString(3, memberName);
            stmt.setString(4, role);
            stmt.executeUpdate();
        }
    }

    private void deleteInvite(Connection conn, long teamId, UUID invitedUuid) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM emp_team_invites WHERE team_id = ? AND invited_uuid = ?")) {
            stmt.setLong(1, teamId);
            stmt.setString(2, invitedUuid.toString());
            stmt.executeUpdate();
        }
    }

    public record TeamMembership(long teamId, String teamName, UUID ownerUuid, String ownerName,
                                 UUID memberUuid, String memberName, String role) {
    }

    public record TeamInvite(long teamId, String invitedUuid, String invitedName, UUID inviterUuid,
                             String inviterName, long expiresAt, String teamName) {
    }

    public record TeamResult(boolean success, String errorKey, TeamMembership membership) {
        public static TeamResult success(TeamMembership membership) {
            return new TeamResult(true, null, membership);
        }

        public static TeamResult fail(String errorKey) {
            return new TeamResult(false, errorKey, null);
        }
    }
}
