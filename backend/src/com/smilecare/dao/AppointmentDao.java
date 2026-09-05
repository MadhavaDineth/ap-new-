package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Appointment;
import com.smilecare.model.Dentist;
import com.smilecare.model.Patient;
import com.smilecare.model.Treatment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All reads and writes for the appointments table.
 *
 * Every appointment is returned already joined to its patient, dentist and
 * treatment, because that is what both the screens and the billing rules need.
 */
public class AppointmentDao {

    private static final String JOINED =
        "SELECT a.id, a.appointment_no, a.appt_date, a.appt_time, a.status, " +
        "       p.id AS p_id, p.name AS p_name, p.address AS p_address, p.contact_no AS p_contact, " +
        "       d.id AS d_id, d.name AS d_name, d.qualification AS d_qual, " +
        "       d.speciality AS d_spec, d.consultation_fee AS d_fee, d.active AS d_active, " +
        "       t.id AS t_id, t.treatment_type AS t_type, t.base_cost AS t_cost, " +
        "       t.duration_mins AS t_mins, t.active AS t_active " +
        "FROM appointments a " +
        "JOIN patients   p ON p.id = a.patient_id " +
        "JOIN dentists   d ON d.id = a.dentist_id " +
        "JOIN treatments t ON t.id = a.treatment_id ";

    /**
     * The diary. Any of the three filters may be null, in which case it is
     * left out of the WHERE clause. Values are always bound as parameters,
     * never concatenated, so the search box cannot be used for SQL injection.
     */
    public List<Appointment> search(LocalDate date, String status, String text) throws SQLException {
        StringBuilder sql = new StringBuilder(JOINED).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        if (date != null) {
            sql.append("AND a.appt_date = ? ");
            params.add(java.sql.Date.valueOf(date));
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND a.status = ? ");
            params.add(status);
        }
        if (text != null && !text.isBlank()) {
            sql.append("AND (p.name LIKE ? OR a.appointment_no LIKE ? OR p.contact_no LIKE ?) ");
            String like = "%" + text.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY a.appt_date, a.appt_time");

        List<Appointment> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    /** Every visit a patient has ever had, newest first, for their history panel. */
    public List<Appointment> findByPatientId(int patientId) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(
                 JOINED + "WHERE p.id = ? ORDER BY a.appt_date DESC, a.appt_time DESC")) {
            ps.setInt(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Appointment> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    public Optional<Appointment> findByNo(String appointmentNo) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(JOINED + "WHERE a.appointment_no = ?")) {
            ps.setString(1, appointmentNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * True when the dentist already has a live appointment in that slot.
     * Cancelled rows are ignored, so a cancelled slot can be reused.
     */
    public boolean slotTaken(int dentistId, LocalDate date, LocalTime time) throws SQLException {
        String sql = "SELECT 1 FROM appointments " +
                     "WHERE dentist_id = ? AND appt_date = ? AND appt_time = ? " +
                     "AND status <> 'Cancelled' LIMIT 1";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, dentistId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setTime(3, java.sql.Time.valueOf(time));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** APT-1042, APT-1043 and so on. */
    public String nextNumber() throws SQLException {
        String sql = "SELECT IFNULL(MAX(CAST(SUBSTRING(appointment_no, 5) AS UNSIGNED)), 1041) " +
                     "FROM appointments";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return "APT-" + (rs.getLong(1) + 1);
        }
    }

    public int insert(String appointmentNo, int patientId, int dentistId, int treatmentId,
                      LocalDate date, LocalTime time, Integer createdBy) throws SQLException {
        String sql = "INSERT INTO appointments " +
                     "(appointment_no, patient_id, dentist_id, treatment_id, appt_date, appt_time, status, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'Pending', ?)";
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, appointmentNo);
            ps.setInt(2, patientId);
            ps.setInt(3, dentistId);
            ps.setInt(4, treatmentId);
            ps.setDate(5, java.sql.Date.valueOf(date));
            ps.setTime(6, java.sql.Time.valueOf(time));
            if (createdBy == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
            } else {
                ps.setInt(7, createdBy);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public boolean updateStatus(String appointmentNo, String status) throws SQLException {
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE appointments SET status = ? WHERE appointment_no = ?")) {
            ps.setString(1, status);
            ps.setString(2, appointmentNo);
            return ps.executeUpdate() > 0;
        }
    }

    private static Appointment map(ResultSet rs) throws SQLException {
        Patient patient = new Patient(
            rs.getInt("p_id"), rs.getString("p_name"),
            rs.getString("p_address"), rs.getString("p_contact"));

        Dentist dentist = new Dentist(
            rs.getInt("d_id"), rs.getString("d_name"), rs.getString("d_qual"),
            rs.getString("d_spec"), rs.getBigDecimal("d_fee"), rs.getBoolean("d_active"));

        Treatment treatment = new Treatment(
            rs.getInt("t_id"), rs.getString("t_type"),
            rs.getBigDecimal("t_cost"), rs.getInt("t_mins"), rs.getBoolean("t_active"));

        return new Appointment(
            rs.getInt("id"),
            rs.getString("appointment_no"),
            patient, dentist, treatment,
            rs.getDate("appt_date").toLocalDate(),
            rs.getTime("appt_time").toLocalTime(),
            rs.getString("status"));
    }
}
