package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Bill;

import java.time.format.DateTimeFormatter;

/**
 * Observer that sends the patient their confirmation and reminder.
 *
 * The message is written to the console rather than actually posted, because
 * the clinic has no mail server configured yet. Everything needed to send it
 * for real is here: drop the JavaMail jar into backend/lib and replace the
 * body of {@link #send} with a Transport.send call. No other class changes.
 *
 * Every send is also written to the notifications table, so the Reminders
 * screen shows exactly what this class has sent, in order.
 */
public class EmailListener implements AppointmentListener {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Override
    public void onCreated(Appointment a, Integer byUserId) {
        send(a.appointmentNo(), a.patient().contactNo(), "Confirmation",
            "Appointment " + a.appointmentNo() + " confirmed",
            "Dear " + a.patient().name() + ", your appointment with " + a.dentist().name()
                + " for " + a.treatment().treatmentType() + " is booked for "
                + a.date().format(DAY) + " at " + a.time() + ". "
                + "Please quote " + a.appointmentNo() + " when you arrive.");
    }

    @Override
    public void onStatusChanged(Appointment a, String previousStatus, Integer byUserId) {
        if (Appointment.CANCELLED.equals(a.status())) {
            send(a.appointmentNo(), a.patient().contactNo(), "Cancellation",
                "Appointment " + a.appointmentNo() + " cancelled",
                "Dear " + a.patient().name() + ", your appointment on " + a.date().format(DAY)
                    + " has been cancelled. Call 011 234 5678 to rebook.");
        }
    }

    @Override
    public void onBillIssued(Bill bill, Integer byUserId) {
        send(bill.appointmentNo(), null, "Receipt",
            "Receipt " + bill.billNo(),
            "Total paid Rs. " + bill.total() + " for " + bill.appointmentNo() + ".");
    }

    private void send(String appointmentNo, String to, String type, String subject, String body) {
        System.out.println("[mail] to=" + (to == null ? "clinic" : to)
            + " | " + subject + " | " + body);
        DaoFactory.notifications().insert(appointmentNo, to, type, subject, body);
    }
}
