package com.smilecare.model;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * One appointment, already joined to its patient, dentist and treatment.
 *
 * The JSON shape is flat because that is what the front end reads; the object
 * itself keeps the related DTOs so business rules can use them.
 */
public record Appointment(int id,
                          String appointmentNo,
                          Patient patient,
                          Dentist dentist,
                          Treatment treatment,
                          LocalDate date,
                          LocalTime time,
                          String status) {

    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /** Statuses the system understands, in the order a visit moves through them. */
    public static final String PENDING   = "Pending";
    public static final String CONFIRMED = "Confirmed";
    public static final String COMPLETED = "Completed";
    public static final String CANCELLED = "Cancelled";

    public static boolean isValidStatus(String value) {
        return PENDING.equals(value) || CONFIRMED.equals(value)
            || COMPLETED.equals(value) || CANCELLED.equals(value);
    }

    public BigDecimal treatmentCost()   { return treatment.baseCost(); }
    public BigDecimal consultationFee() { return dentist.consultationFee(); }

    public JSONObject toJson() {
        return new JSONObject()
            .put("appointmentNo", appointmentNo)
            .put("patientName", patient.name())
            .put("address", patient.address())
            .put("contactNo", patient.contactNo())
            .put("dentistId", dentist.id())
            .put("dentistName", dentist.name())
            .put("consultationFee", dentist.consultationFee())
            .put("treatmentId", treatment.id())
            .put("treatmentType", treatment.treatmentType())
            .put("treatmentCost", treatment.baseCost())
            .put("date", date.toString())
            .put("time", time.format(TIME_FMT))
            .put("status", status);
    }
}
