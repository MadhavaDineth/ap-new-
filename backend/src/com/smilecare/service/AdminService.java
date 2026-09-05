package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.AuditEntry;
import com.smilecare.model.Dentist;
import com.smilecare.model.Treatment;
import com.smilecare.model.User;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Everything the admin panel is allowed to change: the dentists, the treatment
 * price list and the staff accounts.
 *
 * Nothing here is ever deleted. A dentist who leaves or a treatment that is
 * withdrawn is switched to inactive instead, so the appointments and bills that
 * point at them keep making sense - a foreign key to a deleted row is a broken
 * receipt. The screens simply stop offering inactive rows.
 *
 * Every change is written to the audit log, so "who changed this price" always
 * has an answer.
 */
public class AdminService {

    private static final Set<String> ROLES = Set.of("admin", "receptionist", "dentist");

    /* ================================================================
       dentists
       ================================================================ */

    public List<Dentist> dentists() {
        try {
            return DaoFactory.dentists().findAllIncludingInactive();
        } catch (SQLException e) {
            throw read("dentist list", e);
        }
    }

    public Dentist createDentist(int byUserId, String name, String qualification,
                                 String speciality, BigDecimal fee) {
        String cleanName = required(name, "Enter the name of the dentist.");
        checkFee(fee);
        try {
            int id = DaoFactory.dentists()
                .insert(cleanName, trim(qualification), trim(speciality), fee);
            audit(byUserId, "CREATE_DENTIST", "dentists", cleanName,
                  "consultation fee " + fee);
            return DaoFactory.dentists().findById(id).orElseThrow(
                () -> new AppException(500, "The dentist was saved but could not be read back."));
        } catch (SQLException e) {
            throw write("dentist", e);
        }
    }

    public Dentist updateDentist(int byUserId, int id, String name, String qualification,
                                 String speciality, BigDecimal fee, boolean active) {
        String cleanName = required(name, "Enter the name of the dentist.");
        checkFee(fee);
        try {
            boolean changed = DaoFactory.dentists()
                .update(id, cleanName, trim(qualification), trim(speciality), fee, active);
            if (!changed) {
                throw AppException.notFound("That dentist is not on file.");
            }
            audit(byUserId, "UPDATE_DENTIST", "dentists", cleanName,
                  "fee " + fee + ", " + (active ? "active" : "retired"));
            return DaoFactory.dentists().findById(id).orElseThrow(
                () -> AppException.notFound("That dentist is not on file."));
        } catch (SQLException e) {
            throw write("dentist", e);
        }
    }

    /* ================================================================
       treatments
       ================================================================ */

    public List<Treatment> treatments() {
        try {
            return DaoFactory.treatments().findAllIncludingInactive();
        } catch (SQLException e) {
            throw read("treatment list", e);
        }
    }

    public Treatment createTreatment(int byUserId, String type, BigDecimal cost, int minutes) {
        String cleanType = required(type, "Enter the name of the treatment.");
        checkCost(cost);
        int safeMinutes = checkMinutes(minutes);
        try {
            int id = DaoFactory.treatments().insert(cleanType, cost, safeMinutes);
            audit(byUserId, "CREATE_TREATMENT", "treatments", cleanType, "cost " + cost);
            return DaoFactory.treatments().findById(id).orElseThrow(
                () -> new AppException(500, "The treatment was saved but could not be read back."));
        } catch (SQLException e) {
            throw write("treatment", e);
        }
    }

    public Treatment updateTreatment(int byUserId, int id, String type, BigDecimal cost,
                                     int minutes, boolean active) {
        String cleanType = required(type, "Enter the name of the treatment.");
        checkCost(cost);
        int safeMinutes = checkMinutes(minutes);
        try {
            boolean changed = DaoFactory.treatments()
                .update(id, cleanType, cost, safeMinutes, active);
            if (!changed) {
                throw AppException.notFound("That treatment is not on file.");
            }
            audit(byUserId, "UPDATE_TREATMENT", "treatments", cleanType,
                  "cost " + cost + ", " + (active ? "offered" : "withdrawn"));
            return DaoFactory.treatments().findById(id).orElseThrow(
                () -> AppException.notFound("That treatment is not on file."));
        } catch (SQLException e) {
            throw write("treatment", e);
        }
    }

    /* ================================================================
       staff accounts
       ================================================================ */

    public List<User> users() {
        try {
            return DaoFactory.users().findAll();
        } catch (SQLException e) {
            throw read("staff list", e);
        }
    }

    public User createUser(int byUserId, String username, String fullName,
                           String role, String password) {
        String cleanUser = required(username, "Enter a username.").toLowerCase();
        String cleanName = required(fullName, "Enter the full name of the staff member.");
        String cleanRole = checkRole(role);
        checkPassword(password);

        if (!cleanUser.matches("[a-z0-9._-]{3,40}")) {
            throw AppException.badRequest(
                "A username may only use letters, numbers, dot, dash and underscore, and must be at least 3 characters.");
        }

        try {
            if (DaoFactory.users().usernameTaken(cleanUser)) {
                throw AppException.conflict("The username " + cleanUser + " is already in use.");
            }
            int id = DaoFactory.users()
                .insert(cleanUser, AuthService.sha256(password), cleanRole, cleanName);
            audit(byUserId, "CREATE_USER", "users", cleanUser, "role " + cleanRole);
            return DaoFactory.users().findById(id).orElseThrow(
                () -> new AppException(500, "The account was created but could not be read back."));
        } catch (SQLException e) {
            throw write("staff account", e);
        }
    }

    /**
     * Changes the name, the role and whether the account may sign in. A new
     * password is optional; leaving it blank keeps the existing one.
     */
    public User updateUser(int byUserId, int id, String fullName, String role,
                           boolean active, String newPassword) {
        String cleanName = required(fullName, "Enter the full name of the staff member.");
        String cleanRole = checkRole(role);

        try {
            User existing = DaoFactory.users().findById(id).orElseThrow(
                () -> AppException.notFound("That staff account is not on file."));

            // the clinic must never be left without a way back in
            boolean losingAdmin = "admin".equalsIgnoreCase(existing.role())
                && existing.active()
                && (!"admin".equals(cleanRole) || !active);
            if (losingAdmin && DaoFactory.users().activeAdminCount() <= 1) {
                throw AppException.conflict(
                    "This is the last active administrator. Give another account the admin role first.");
            }

            DaoFactory.users().update(id, cleanName, cleanRole, active);
            audit(byUserId, "UPDATE_USER", "users", existing.username(),
                  "role " + cleanRole + ", " + (active ? "active" : "suspended"));

            if (newPassword != null && !newPassword.isBlank()) {
                checkPassword(newPassword);
                DaoFactory.users().resetPassword(id, AuthService.sha256(newPassword));
                audit(byUserId, "RESET_PASSWORD", "users", existing.username(), null);
            }

            return DaoFactory.users().findById(id).orElseThrow(
                () -> AppException.notFound("That staff account is not on file."));
        } catch (SQLException e) {
            throw write("staff account", e);
        }
    }

    /* ================================================================
       audit trail
       ================================================================ */

    public List<AuditEntry> audit(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));
        try {
            return DaoFactory.audit().recent(safe);
        } catch (SQLException e) {
            throw read("audit log", e);
        }
    }

    /* ================================================================
       validation helpers
       ================================================================ */

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw AppException.badRequest(message);
        }
        return value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void checkFee(BigDecimal fee) {
        if (fee == null || fee.signum() < 0) {
            throw AppException.badRequest("Enter the consultation fee as a number, zero or more.");
        }
    }

    private static void checkCost(BigDecimal cost) {
        if (cost == null || cost.signum() < 0) {
            throw AppException.badRequest("Enter the treatment cost as a number, zero or more.");
        }
    }

    private static int checkMinutes(int minutes) {
        if (minutes < 5 || minutes > 480) {
            throw AppException.badRequest("A treatment takes between 5 and 480 minutes.");
        }
        return minutes;
    }

    private static String checkRole(String role) {
        String clean = role == null ? "" : role.trim().toLowerCase();
        if (!ROLES.contains(clean)) {
            throw AppException.badRequest("Choose one of: admin, receptionist, dentist.");
        }
        return clean;
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 6) {
            throw AppException.badRequest("A password must be at least 6 characters long.");
        }
    }

    private static void audit(int userId, String action, String entity, String ref, String details) {
        DaoFactory.audit().log(userId, action, entity, ref, details);
    }

    private static AppException read(String what, SQLException e) {
        return new AppException(500, "The " + what + " could not be read: " + e.getMessage());
    }

    private static AppException write(String what, SQLException e) {
        return new AppException(500, "The " + what + " could not be saved: " + e.getMessage());
    }
}
