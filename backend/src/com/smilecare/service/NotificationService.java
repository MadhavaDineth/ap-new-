package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Notification;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The log behind the Reminders screen, and the manual "Send reminder" button
 * on the Find appointment screen. Automatic sends are logged by
 * {@link EmailListener}; this class is only for the one a member of staff
 * triggers by hand.
 */
public class NotificationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final AppointmentService appointments;

    public NotificationService(AppointmentService appointments) {
        this.appointments = appointments;
    }

    public List<Notification> recent(int limit) {
        int safe = Math.max(1, Math.min(limit, 200));
        try {
            return DaoFactory.notifications().recent(safe);
        } catch (SQLException e) {
            throw new AppException(500, "The reminders log could not be read: " + e.getMessage());
        }
    }

    /** Sends (logs) a reminder for a visit that has not happened yet. */
    public Notification sendReminder(String appointmentNo, Integer byUserId) {
        Appointment a = appointments.require(appointmentNo);
        if (Appointment.CANCELLED.equals(a.status()) || Appointment.COMPLETED.equals(a.status())) {
            throw AppException.conflict(
                "A reminder cannot be sent for an appointment that is " + a.status().toLowerCase() + ".");
        }

        String subject = "Reminder: appointment " + a.appointmentNo();
        String message = "Dear " + a.patient().name() + ", this is a reminder of your appointment with "
            + a.dentist().name() + " for " + a.treatment().treatmentType() + " on "
            + a.date().format(DAY) + " at " + a.time() + ". Please quote " + a.appointmentNo()
            + " when you arrive.";

        int id = DaoFactory.notifications().insert(a.appointmentNo(), a.patient().contactNo(),
            "Reminder", subject, message);
        DaoFactory.audit().log(byUserId, "SEND_REMINDER", "appointments", a.appointmentNo(), null);

        System.out.println("[mail] to=" + a.patient().contactNo() + " | " + subject + " | " + message);

        return new Notification(id, a.appointmentNo(), a.patient().contactNo(),
            "Reminder", subject, message, LocalDateTime.now().toString());
    }
}
