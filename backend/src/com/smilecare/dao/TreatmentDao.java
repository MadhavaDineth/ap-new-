package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Treatment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TreatmentDao implements Dao<Treatment> {

    private static final String SELECT =
        "SELECT id, treatment_type, base_cost, duration_mins, active FROM treatments";

    @Override
    public List<Treatment> findAll() throws SQLException {
        List<Treatment> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE active = 1 ORDER BY base_cost");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<Treatment> findById(int id) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    static Treatment map(ResultSet rs) throws SQLException {
        return new Treatment(
            rs.getInt("id"),
            rs.getString("treatment_type"),
            rs.getBigDecimal("base_cost"),
            rs.getInt("duration_mins"),
            rs.getBoolean("active"));
    }

    /* ----------------------------------------------------------------
       Used by the admin panel only. findAll() hides withdrawn treatments
       so they never appear on the booking screen; this one shows them all.
       ---------------------------------------------------------------- */

    public List<Treatment> findAllIncludingInactive() throws SQLException {
        List<Treatment> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY active DESC, base_cost");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    /** @return the id given to the new row */
    public int insert(String type, java.math.BigDecimal cost, int minutes) throws SQLException {
        String sql = "INSERT INTO treatments (treatment_type, base_cost, duration_mins) VALUES (?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type);
            ps.setBigDecimal(2, cost);
            ps.setInt(3, minutes);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean update(int id, String type, java.math.BigDecimal cost,
                          int minutes, boolean active) throws SQLException {
        String sql = "UPDATE treatments SET treatment_type = ?, base_cost = ?, " +
                     "duration_mins = ?, active = ? WHERE id = ?";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setBigDecimal(2, cost);
            ps.setInt(3, minutes);
            ps.setBoolean(4, active);
            ps.setInt(5, id);
            return ps.executeUpdate() > 0;
        }
    }
}
