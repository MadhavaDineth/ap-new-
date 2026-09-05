package com.smilecare.model;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A receipt. The total is worked out by a pricing strategy rather than being
 * hard coded here, so the clinic can change how it charges without touching
 * the DTO or the DAO.
 */
public record Bill(String billNo,
                   String appointmentNo,
                   String patientName,
                   String dentistName,
                   String treatmentType,
                   BigDecimal treatmentCost,
                   BigDecimal consultationFee,
                   BigDecimal total,
                   String pricingStrategy,
                   LocalDateTime issuedAt) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("billNo", billNo)
            .put("appointmentNo", appointmentNo)
            .put("patientName", patientName)
            .put("dentistName", dentistName)
            .put("treatmentType", treatmentType)
            .put("treatmentCost", treatmentCost)
            .put("consultationFee", consultationFee)
            .put("total", total)
            .put("pricingStrategy", pricingStrategy)
            .put("issuedAt", issuedAt.toString());
    }
}
