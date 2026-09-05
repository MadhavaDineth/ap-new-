package com.smilecare.dao;

import com.smilecare.db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Writes the audit trail. Failing to log must never break the operation that
 * was being logged, so problems here are reported and swallowed.
 */
public class AuditDao {

    public void log(Integer userId, String action, String entity, String ref, String details) {
        String sql = "INSERT INTO audit_log (user_id, action, entity, entity_ref, details) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, action);
            ps.setString(3, entity);
            ps.setString(4, ref);
            ps.setString(5, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[audit] could not write the log entry: " + e.getMessage());
        }
    }

    /** The newest entries, with the name of whoever did it, for the admin screen. */
    public java.util.List<com.smilecare.model.AuditEntry> recent(int limit) throws SQLException {
        String sql = "SELECT l.id, l.action, l.entity, l.entity_ref, l.details, l.logged_at, " +
                     "       IFNULL(u.full_name, 'system') AS who " +
                     "FROM audit_log l LEFT JOIN users u ON u.id = l.user_id " +
                     "ORDER BY l.id DESC LIMIT ?";

        java.util.List<com.smilecare.model.AuditEntry> out = new java.util.ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new com.smilecare.model.AuditEntry(
                        rs.getInt("id"),
                        rs.getString("who"),
                        rs.getString("action"),
                        rs.getString("entity"),
                        rs.getString("entity_ref"),
                        rs.getString("details"),
                        rs.getTimestamp("logged_at").toLocalDateTime().toString()));
                }
            }
        }
        return out;
    }
}
