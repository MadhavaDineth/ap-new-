package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Dentist;
import com.smilecare.model.Patient;
import com.smilecare.model.Treatment;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The booking rules of the clinic, kept away from both the database code and
 * the HTTP code.
 *
 * This class is also the SUBJECT of the observer pattern: it tells its
 * listeners what happened, without knowing or caring what they do about it.
 */
public class AppointmentService {

    private final List<AppointmentListener> listeners = new ArrayList<>();

    public void addListener(AppointmentListener listener) {
        listeners.add(listener);
    }

    public List<Appointment> list(LocalDate date, String status, String text) {
        try {
            return DaoFactory.appointments().search(date, status, text);
        } catch (SQLException e) {
            throw new AppException(500, "The diary could not be read: " + e.getMessage());
        }
    }

    public Appointment require(String appointmentNo) {
        return find(appointmentNo).orElseThrow(
            () -> AppException.notFound("No appointment found for " + appointmentNo + "."));
    }

    public Optional<Appointment> find(String appointmentNo) {
        try {
            return DaoFactory.appointments().findByNo(appointmentNo.toUpperCase());
        } catch (SQLException e) {
            throw new AppException(500, "The appointment could not be read: " + e.getMessage());
        }
    }

    /**
     * Registers a new appointment.
     *
     * The patient is matched on the contact number so returning patients are
     * not duplicated, the slot is checked before anything is written, and the
     * appointment number is generated here rather than typed by staff.
     */
    public Appointment create(String patientName, String address, String contactNo,
                              int dentistId, int treatmentId,
                              LocalDate date, LocalTime time, Integer byUserId) {

        if (patientName == null || patientName.trim().length() < 3) {
            throw AppException.badRequest("Enter the full name of the patient.");
        }
        if (contactNo == null || !contactNo.trim().matches("0\\d{9}")) {
            throw AppException.badRequest("Enter a 10 digit contact number starting with 0.");
        }
        if (address == null || address.trim().length() < 5) {
            throw AppException.badRequest("Enter the address of the patient.");
        }
        if (date.isBefore(LocalDate.now())) {
            throw AppException.badRequest("An appointment cannot be booked in the past.");
        }

        try {
            Dentist dentist = DaoFactory.dentists().findById(dentistId)
                .orElseThrow(() -> AppException.badRequest("That dentist is not on the list."));
            Treatment treatment = DaoFactory.treatments().findById(treatmentId)
                .orElseThrow(() -> AppException.badRequest("That treatment is not on the list."));

            if (DaoFactory.appointments().slotTaken(dentist.id(), date, time)) {
                throw AppException.conflict(
                    "That dentist already has a patient booked at this time.");
            }

            Patient patient = DaoFactory.patients()
                .findOrCreate(patientName.trim(), address.trim(), contactNo.trim());

            String number = DaoFactory.appointments().nextNumber();
            DaoFactory.appointments().insert(number, patient.id(), dentist.id(),
                treatment.id(), date, time, byUserId);

            Appointment saved = require(number);
            listeners.forEach(l -> l.onCreated(saved, byUserId));
            return saved;

        } catch (SQLException e) {
            throw new AppException(500, "The appointment could not be saved: " + e.getMessage());
        }
    }

    /** Moves a visit between Pending, Confirmed, Completed and Cancelled. */
    public Appointment changeStatus(String appointmentNo, String status, Integer byUserId) {
        if (!Appointment.isValidStatus(status)) {
            throw AppException.badRequest(
                "Status must be Pending, Confirmed, Completed or Cancelled.");
        }

        Appointment before = require(appointmentNo);
        if (before.status().equals(status)) {
            return before;
        }
        if (Appointment.COMPLETED.equals(before.status())) {
            throw AppException.conflict(
                "This visit has been billed already, so its status cannot be changed.");
        }

        try {
            DaoFactory.appointments().updateStatus(before.appointmentNo(), status);
        } catch (SQLException e) {
            throw new AppException(500, "The status could not be changed: " + e.getMessage());
        }

        Appointment after = require(before.appointmentNo());
        listeners.forEach(l -> l.onStatusChanged(after, before.status(), byUserId));
        return after;
    }

    /** Called by BillingService so bill events reach the same listeners. */
    public List<AppointmentListener> listeners() {
        return listeners;
    }
}
