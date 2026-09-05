package com.smilecare.dao;

import com.smilecare.db.DbConnection;
import com.smilecare.model.Report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only queries behind the reports screen.
 *
 * There is no findAll/findById here, so this class does not implement Dao:
 * a report is a question about many rows, not a row that can be fetched and
 * saved back. Every total is worked out by MySQL rather than in Java, because
 * the database is far better at counting than a loop over a result set.
 */
public class ReportDao {

    /** The four figures along the top of the screen. */
    public Report.Summary summary() throws SQLException {
        String sql =
            "SELECT " +
            " (SELECT COUNT(*) FROM appointments " +
            "   WHERE appt_date = CURDATE() AND status <> 'Cancelled')                AS today_count, " +
            " (SELECT COUNT(*) FROM appointments " +
            "   WHERE status = 'Pending' AND appt_date >= CURDATE())                  AS pending_count, " +
            " (SELECT IFNULL(SUM(total), 0) FROM bills WHERE DATE(issued_at) = CURDATE()) AS today_revenue, " +
            " (SELECT IFNULL(SUM(total), 0) FROM bills " +
            "   WHERE YEAR(issued_at) = YEAR(CURDATE()) " +
            "     AND MONTH(issued_at) = MONTH(CURDATE()))                            AS month_revenue, " +
            " (SELECT COUNT(*) FROM bills " +
            "   WHERE YEAR(issued_at) = YEAR(CURDATE()) " +
            "     AND MONTH(issued_at) = MONTH(CURDATE()))                            AS month_bills, " +
            " (SELECT COUNT(*) FROM patients)                                         AS patient_count";

        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new Report.Summary(
                rs.getInt("today_count"),
                rs.getInt("pending_count"),
                rs.getBigDecimal("today_revenue"),
                rs.getBigDecimal("month_revenue"),
                rs.getInt("month_bills"),
                rs.getInt("patient_count"));
        }
    }

    /**
     * Revenue for each of the last n days. Days with no bills are simply
     * missing from the list; the chart fills those gaps with a zero, which
     * keeps this query simple and works on any MySQL version.
     */
    public List<Report.DayRevenue> revenueByDay(int days) throws SQLException {
        String sql =
            "SELECT DATE(issued_at) AS day, COUNT(*) AS bills, SUM(total) AS revenue " +
            "FROM bills " +
            "WHERE issued_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
            "GROUP BY day ORDER BY day";

        List<Report.DayRevenue> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Report.DayRevenue(
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("bills"),
                        rs.getBigDecimal("revenue")));
                }
            }
        }
        return out;
    }

    /** How many appointments sit on each of the last n days. */
    public List<Report.DayCount> appointmentsByDay(int days) throws SQLException {
        String sql =
            "SELECT appt_date AS day, COUNT(*) AS total, " +
            "       SUM(status = 'Cancelled') AS cancelled " +
            "FROM appointments " +
            "WHERE appt_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
            "GROUP BY day ORDER BY day";

        List<Report.DayCount> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Report.DayCount(
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("total"),
                        rs.getInt("cancelled")));
                }
            }
        }
        return out;
    }

    /**
     * Revenue for each day in an explicit range, inclusive. Used by the
     * reports screen when a specific "from" and "to" date are chosen, rather
     * than the last n days.
     */
    public List<Report.DayRevenue> revenueByRange(java.time.LocalDate from, java.time.LocalDate to)
            throws SQLException {
        String sql =
            "SELECT DATE(issued_at) AS day, COUNT(*) AS bills, SUM(total) AS revenue " +
            "FROM bills " +
            "WHERE DATE(issued_at) BETWEEN ? AND ? " +
            "GROUP BY day ORDER BY day";

        List<Report.DayRevenue> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Report.DayRevenue(
                        rs.getDate("day").toLocalDate(),
                        rs.getInt("bills"),
                        rs.getBigDecimal("revenue")));
                }
            }
        }
        return out;
    }

    /** The patients who have spent the most, for the reports screen. */
    public List<Report.PatientCount> topPatients(int limit) throws SQLException {
        String sql =
            "SELECT p.name AS patient, COUNT(*) AS visits, SUM(b.total) AS spent " +
            "FROM bills b " +
            "JOIN appointments a ON a.id = b.appointment_id " +
            "JOIN patients p ON p.id = a.patient_id " +
            "GROUP BY p.id, p.name ORDER BY spent DESC LIMIT ?";

        List<Report.PatientCount> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Report.PatientCount(
                        rs.getString("patient"),
                        rs.getInt("visits"),
                        rs.getBigDecimal("spent")));
                }
            }
        }
        return out;
    }

    /** Which treatments are booked most often. Cancelled visits do not count. */
    public List<Report.TreatmentCount> treatmentPopularity(int limit) throws SQLException {
        String sql =
            "SELECT t.treatment_type AS treatment, COUNT(*) AS bookings, " +
            "       SUM(t.base_cost) AS value " +
            "FROM appointments a JOIN treatments t ON t.id = a.treatment_id " +
            "WHERE a.status <> 'Cancelled' " +
            "GROUP BY t.id, t.treatment_type " +
            "ORDER BY bookings DESC, value DESC LIMIT ?";

        List<Report.TreatmentCount> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Report.TreatmentCount(
                        rs.getString("treatment"),
                        rs.getInt("bookings"),
                        rs.getBigDecimal("value")));
                }
            }
        }
        return out;
    }

    /** How the whole diary splits across the four statuses. */
    public Report.StatusMix statusMix() throws SQLException {
        String sql = "SELECT status, COUNT(*) AS n FROM appointments GROUP BY status";
        int pending = 0, confirmed = 0, completed = 0, cancelled = 0;

        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int n = rs.getInt("n");
                switch (rs.getString("status")) {
                    case "Pending"   -> pending = n;
                    case "Confirmed" -> confirmed = n;
                    case "Completed" -> completed = n;
                    case "Cancelled" -> cancelled = n;
                    default -> { /* a status the screen does not know about */ }
                }
            }
        }
        return new Report.StatusMix(pending, confirmed, completed, cancelled);
    }

    /** How busy each dentist is. A dentist with no appointments still appears. */
    public List<Report.Workload> dentistWorkload() throws SQLException {
        String sql =
            "SELECT d.name AS dentist, COUNT(a.id) AS appointments, " +
            "       IFNULL(SUM(a.status = 'Completed'), 0) AS completed " +
            "FROM dentists d LEFT JOIN appointments a ON a.dentist_id = d.id " +
            "WHERE d.active = 1 " +
            "GROUP BY d.id, d.name ORDER BY appointments DESC, d.name";

        List<Report.Workload> out = new ArrayList<>();
        try (Connection c = DbConnection.get().open();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Report.Workload(
                    rs.getString("dentist"),
                    rs.getInt("appointments"),
                    rs.getInt("completed")));
            }
        }
        return out;
    }
}
