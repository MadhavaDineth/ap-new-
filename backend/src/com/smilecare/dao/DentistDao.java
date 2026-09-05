package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Dentist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DentistDao implements Dao<Dentist> {

    private static final String SELECT =
        "SELECT id, name, qualification, speciality, consultation_fee, active FROM dentists";

    @Override
    public List<Dentist> findAll() throws SQLException {
        List<Dentist> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE active = 1 ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<Dentist> findById(int id) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    static Dentist map(ResultSet rs) throws SQLException {
        return new Dentist(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("qualification"),
            rs.getString("speciality"),
            rs.getBigDecimal("consultation_fee"),
            rs.getBoolean("active"));
    }

    /* ----------------------------------------------------------------
       Used by the admin panel only. findAll() hides retired dentists so
       they never appear on the booking screen; this one shows everybody.
       ---------------------------------------------------------------- */

    public List<Dentist> findAllIncludingInactive() throws SQLException {
        List<Dentist> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY active DESC, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    /** @return the id given to the new row */
    public int insert(String name, String qualification, String speciality,
                      java.math.BigDecimal fee) throws SQLException {
        String sql = "INSERT INTO dentists (name, qualification, speciality, consultation_fee) " +
                     "VALUES (?, ?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, qualification);
            ps.setString(3, speciality);
            ps.setBigDecimal(4, fee);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean update(int id, String name, String qualification, String speciality,
                          java.math.BigDecimal fee, boolean active) throws SQLException {
        String sql = "UPDATE dentists SET name = ?, qualification = ?, speciality = ?, " +
                     "consultation_fee = ?, active = ? WHERE id = ?";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, qualification);
            ps.setString(3, speciality);
            ps.setBigDecimal(4, fee);
            ps.setBoolean(5, active);
            ps.setInt(6, id);
            return ps.executeUpdate() > 0;
        }
    }
}
