package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The consultation fee is waived down by a flat percentage for senior
 * citizens; the treatment itself is charged in full. Reception picks this
 * strategy at the billing screen after checking the patient's ID - there is
 * no age stored on the patient record.
 */
public class SeniorCitizenPricing implements PricingStrategy {

    private static final BigDecimal FEE_DISCOUNT_PERCENT = BigDecimal.valueOf(15);

    @Override
    public String name() {
        return "senior citizen (15% off consultation)";
    }

    @Override
    public BigDecimal treatmentCost(Appointment appointment) {
        return appointment.treatmentCost();
    }

    @Override
    public BigDecimal consultationFee(Appointment appointment) {
        BigDecimal full = appointment.consultationFee();
        BigDecimal keep = BigDecimal.valueOf(100).subtract(FEE_DISCOUNT_PERCENT);
        return full.multiply(keep).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
