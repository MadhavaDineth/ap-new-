package com.smilecare.service;

import com.smilecare.model.Appointment;
import com.smilecare.model.Bill;

/**
 * Design pattern: OBSERVER.
 *
 * Booking an appointment has side effects that have nothing to do with saving
 * a row: the audit trail has to be written, the patient has to be e-mailed.
 * Each of those is a listener registered with AppointmentService, which simply
 * announces what happened. Adding a text-message reminder later means writing
 * one more listener, not editing the booking logic.
 */
public interface AppointmentListener {

    default void onCreated(Appointment appointment, Integer byUserId) { }

    default void onStatusChanged(Appointment appointment, String previousStatus, Integer byUserId) { }

    default void onBillIssued(Bill bill, Integer byUserId) { }
}
