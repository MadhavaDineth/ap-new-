package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDao implements Dao<User> {

    private static final String SELECT =
        "SELECT id, username, full_name, role, active FROM users";

    @Override
    public List<User> findAll() throws SQLException {
        List<User> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<User> findById(int id) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * The hash is compared inside SQL, so the stored password never travels
     * back into the program. An unknown username and a wrong password give
     * exactly the same empty result, which stops an attacker learning which
     * usernames exist. A suspended account cannot sign in at all.
     */
    public Optional<User> authenticate(String username, String passwordHash) throws SQLException {
        String sql = SELECT + " WHERE username = ? AND password = ? AND active = 1";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("full_name"),
            rs.getString("role"),
            rs.getBoolean("active"));
    }

    /* ---------------- admin panel ---------------- */

    public boolean usernameTaken(String username) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** @param passwordHash already SHA-256 hex; the plain password never gets here */
    public int insert(String username, String passwordHash, String role, String fullName)
            throws SQLException {
        String sql = "INSERT INTO users (username, password, role, full_name) VALUES (?, ?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role);
            ps.setString(4, fullName);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean update(int id, String fullName, String role, boolean active) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, role = ?, active = ? WHERE id = ?";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, role);
            ps.setBoolean(3, active);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean resetPassword(int id, String passwordHash) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
            ps.setString(1, passwordHash);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** How many administrators are still able to sign in. */
    public int activeAdminCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'admin' AND active = 1";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
