package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Report;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * The business layer in front of ReportDao.
 *
 * It does two things the DAO deliberately does not: it keeps the ranges the
 * screen may ask for inside sensible limits, and it turns a database failure
 * into an AppException carrying a message the front desk can read.
 */
public class ReportService {

    /** A chart of more than a quarter is unreadable, and one of no days is empty. */
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;

    public Report.Summary summary() {
        try {
            return DaoFactory.reports().summary();
        } catch (SQLException e) {
            throw failed("summary", e);
        }
    }

    public List<Report.DayRevenue> revenue(int days) {
        try {
            return DaoFactory.reports().revenueByDay(clampDays(days));
        } catch (SQLException e) {
            throw failed("revenue report", e);
        }
    }

    public List<Report.DayCount> appointmentsPerDay(int days) {
        try {
            return DaoFactory.reports().appointmentsByDay(clampDays(days));
        } catch (SQLException e) {
            throw failed("daily appointment report", e);
        }
    }

    /** Revenue for an explicit date range, inclusive. "from" must not be after "to". */
    public List<Report.DayRevenue> revenueRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw AppException.badRequest("The start date must not be after the end date.");
        }
        try {
            return DaoFactory.reports().revenueByRange(from, to);
        } catch (SQLException e) {
            throw failed("revenue report", e);
        }
    }

    public List<Report.PatientCount> topPatients(int limit) {
        int safe = Math.max(1, Math.min(limit, 20));
        try {
            return DaoFactory.reports().topPatients(safe);
        } catch (SQLException e) {
            throw failed("top patients report", e);
        }
    }

    public List<Report.TreatmentCount> topTreatments(int limit) {
        int safe = Math.max(1, Math.min(limit, 20));
        try {
            return DaoFactory.reports().treatmentPopularity(safe);
        } catch (SQLException e) {
            throw failed("treatment report", e);
        }
    }

    public Report.StatusMix statusMix() {
        try {
            return DaoFactory.reports().statusMix();
        } catch (SQLException e) {
            throw failed("status breakdown", e);
        }
    }

    public List<Report.Workload> dentistWorkload() {
        try {
            return DaoFactory.reports().dentistWorkload();
        } catch (SQLException e) {
            throw failed("dentist workload report", e);
        }
    }

    private static int clampDays(int days) {
        return Math.max(MIN_DAYS, Math.min(days, MAX_DAYS));
    }

    private static AppException failed(String what, SQLException e) {
        return new AppException(500, "The " + what + " could not be produced: " + e.getMessage());
    }
}
