package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Patient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PatientDao implements Dao<Patient> {

    private static final String SELECT =
        "SELECT id, name, address, contact_no FROM patients";

    @Override
    public List<Patient> findAll() throws SQLException {
        List<Patient> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<Patient> findById(int id) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Matches the name, contact number or address, for the patients screen. */
    public List<Patient> search(String text) throws SQLException {
        if (text == null || text.isBlank()) {
            return findAll();
        }
        String like = "%" + text.trim() + "%";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(
                 SELECT + " WHERE name LIKE ? OR contact_no LIKE ? OR address LIKE ? ORDER BY name")) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<Patient> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public Optional<Patient> findByContact(String contactNo) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(SELECT + " WHERE contact_no = ? LIMIT 1")) {
            ps.setString(1, contactNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Patient insert(String name, String address, String contactNo) throws SQLException {
        String sql = "INSERT INTO patients (name, address, contact_no) VALUES (?, ?, ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, address);
            ps.setString(3, contactNo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Patient(keys.getInt(1), name, address, contactNo);
            }
        }
    }

    /**
     * A returning patient is recognised by the contact number, so the same
     * person is not inserted twice. The address is refreshed if they moved.
     */
    public Patient findOrCreate(String name, String address, String contactNo) throws SQLException {
        Optional<Patient> existing = findByContact(contactNo);
        if (existing.isEmpty()) {
            return insert(name, address, contactNo);
        }

        Patient p = existing.get();
        if (!p.address().equalsIgnoreCase(address) || !p.name().equalsIgnoreCase(name)) {
            try (Connection c = DbConnection.get().open();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE patients SET name = ?, address = ? WHERE id = ?")) {
                ps.setString(1, name);
                ps.setString(2, address);
                ps.setInt(3, p.id());
                ps.executeUpdate();
            }
            return new Patient(p.id(), name, address, contactNo);
        }
        return p;
    }

    public boolean update(int id, String name, String address, String contactNo) throws SQLException {
        String sql = "UPDATE patients SET name = ?, address = ?, contact_no = ? WHERE id = ?";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, address);
            ps.setString(3, contactNo);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Removes the patient outright: there is no history to protect once the
     * service layer has confirmed they have no appointments. A foreign key
     * violation here means that check was skipped, so it is left to surface
     * as a plain SQLException rather than hidden.
     */
    public boolean delete(int id) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM patients WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    static Patient map(ResultSet rs) throws SQLException {
        return new Patient(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("contact_no"));
    }
}
