package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The log behind the Reminders screen: every confirmation, reminder,
 * cancellation notice and receipt e-mail the system has sent.
 */
public class NotificationDao {

    /** @return the id of the new row, or 0 when it could not be written */
    public int insert(String appointmentNo, String recipient, String type,
                      String subject, String message) {
        String sql = "INSERT INTO notifications (appointment_no, recipient, type, subject, message) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, appointmentNo);
            ps.setString(2, recipient);
            ps.setString(3, type);
            ps.setString(4, subject);
            ps.setString(5, message);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            // a notification that could not be logged must never break the
            // booking, cancellation or bill it was raised for
            System.err.println("[notifications] could not write the log entry: " + e.getMessage());
            return 0;
        }
    }

    /** The newest notifications first, for the Reminders screen. */
    public List<Notification> recent(int limit) throws SQLException {
        String sql = "SELECT id, appointment_no, recipient, type, subject, message, sent_at " +
                     "FROM notifications ORDER BY id DESC LIMIT ?";
        List<Notification> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Notification(
                        rs.getInt("id"),
                        rs.getString("appointment_no"),
                        rs.getString("recipient"),
                        rs.getString("type"),
                        rs.getString("subject"),
                        rs.getString("message"),
                        rs.getTimestamp("sent_at").toLocalDateTime().toString()));
                }
            }
        }
        return out;
    }
}
