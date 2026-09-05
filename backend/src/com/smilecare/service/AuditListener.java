package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Bill;

/**
 * Observer that records who did what, in the audit_log table.
 */
public class AuditListener implements AppointmentListener {

    @Override
    public void onCreated(Appointment a, Integer byUserId) {
        DaoFactory.audit().log(byUserId, "CREATE_APPOINTMENT", "appointments", a.appointmentNo(),
            a.patient().name() + " with " + a.dentist().name()
                + " on " + a.date() + " at " + a.time());
    }

    @Override
    public void onStatusChanged(Appointment a, String previousStatus, Integer byUserId) {
        DaoFactory.audit().log(byUserId, "UPDATE_STATUS", "appointments", a.appointmentNo(),
            previousStatus + " -> " + a.status());
    }

    @Override
    public void onBillIssued(Bill bill, Integer byUserId) {
        DaoFactory.audit().log(byUserId, "ISSUE_BILL", "bills", bill.billNo(),
            "Total " + bill.total() + " for " + bill.appointmentNo());
    }
}
