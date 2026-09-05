package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A flat co-payment rule for patients covered by an insurer: the clinic
 * collects a percentage of each charge from the patient at the counter, and
 * claims the rest from the insurance company separately. Both the treatment
 * and the consultation are reduced by the same percentage.
 */
public class InsurancePricing implements PricingStrategy {

    private static final BigDecimal PATIENT_SHARE_PERCENT = BigDecimal.valueOf(80);

    @Override
    public String name() {
        return "insurance (20% co-payment covered)";
    }

    @Override
    public BigDecimal treatmentCost(Appointment appointment) {
        return share(appointment.treatmentCost());
    }

    @Override
    public BigDecimal consultationFee(Appointment appointment) {
        return share(appointment.consultationFee());
    }

    private static BigDecimal share(BigDecimal full) {
        return full.multiply(PATIENT_SHARE_PERCENT).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
