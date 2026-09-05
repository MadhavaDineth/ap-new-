package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Patient;

import java.sql.SQLException;
import java.util.List;

/**
 * Patient records: listing, editing and removing them.
 *
 * A patient with any appointment on file is never deleted - that appointment
 * (and any bill on it) would be left pointing at nothing. The screen is told
 * to edit the record instead, the same way a dentist or treatment is retired
 * rather than erased.
 */
public class PatientService {

    public List<Patient> search(String text) {
        try {
            return DaoFactory.patients().search(text);
        } catch (SQLException e) {
            throw read("patient list", e);
        }
    }

    public Patient require(int id) {
        try {
            return DaoFactory.patients().findById(id).orElseThrow(
                () -> AppException.notFound("That patient is not on file."));
        } catch (SQLException e) {
            throw read("patient", e);
        }
    }

    /** Every visit this patient has had, newest first. */
    public List<Appointment> history(int id) {
        require(id);
        try {
            return DaoFactory.appointments().findByPatientId(id);
        } catch (SQLException e) {
            throw read("treatment history", e);
        }
    }

    public Patient create(int byUserId, String name, String address, String contactNo) {
        String cleanName = required(name, "Enter the name of the patient.");
        String cleanAddress = required(address, "Enter the address of the patient.");
        String cleanContact = checkContact(contactNo);

        try {
            if (DaoFactory.patients().findByContact(cleanContact).isPresent()) {
                throw AppException.conflict(
                    "A patient with contact number " + cleanContact + " is already on file.");
            }
            Patient saved = DaoFactory.patients().insert(cleanName, cleanAddress, cleanContact);
            audit(byUserId, "CREATE_PATIENT", saved.name(), "contact " + cleanContact);
            return saved;
        } catch (SQLException e) {
            throw write("patient", e);
        }
    }

    public Patient update(int byUserId, int id, String name, String address, String contactNo) {
        require(id);
        String cleanName = required(name, "Enter the name of the patient.");
        String cleanAddress = required(address, "Enter the address of the patient.");
        String cleanContact = checkContact(contactNo);

        try {
            DaoFactory.patients().update(id, cleanName, cleanAddress, cleanContact);
            audit(byUserId, "UPDATE_PATIENT", cleanName, "contact " + cleanContact);
            return DaoFactory.patients().findById(id).orElseThrow(
                () -> AppException.notFound("That patient is not on file."));
        } catch (SQLException e) {
            throw write("patient", e);
        }
    }

    public void delete(int byUserId, int id) {
        Patient patient = require(id);
        try {
            if (!DaoFactory.appointments().findByPatientId(id).isEmpty()) {
                throw AppException.conflict(
                    "This patient has appointments on file, so the record cannot be deleted. "
                        + "Their details can still be edited.");
            }
            DaoFactory.patients().delete(id);
            audit(byUserId, "DELETE_PATIENT", patient.name(), "contact " + patient.contactNo());
        } catch (SQLException e) {
            throw write("patient", e);
        }
    }

    /* ----------------------------------------------------------------- */

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw AppException.badRequest(message);
        }
        return value.trim();
    }

    private static String checkContact(String contactNo) {
        String clean = contactNo == null ? "" : contactNo.trim();
        if (!clean.matches("0\\d{9}")) {
            throw AppException.badRequest("Enter a 10 digit contact number starting with 0.");
        }
        return clean;
    }

    private static void audit(int userId, String action, String ref, String details) {
        DaoFactory.audit().log(userId, action, "patients", ref, details);
    }

    private static AppException read(String what, SQLException e) {
        return new AppException(500, "The " + what + " could not be read: " + e.getMessage());
    }

    private static AppException write(String what, SQLException e) {
        return new AppException(500, "The " + what + " could not be saved: " + e.getMessage());
    }
}
