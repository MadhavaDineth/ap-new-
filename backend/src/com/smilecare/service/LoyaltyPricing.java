package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A thank-you discount on the treatment cost for a returning patient; the
 * consultation fee is charged in full. Reception applies this when the
 * patient is a recognised regular.
 */
public class LoyaltyPricing implements PricingStrategy {

    private static final BigDecimal TREATMENT_DISCOUNT_PERCENT = BigDecimal.valueOf(10);

    @Override
    public String name() {
        return "loyalty discount (10% off treatment)";
    }

    @Override
    public BigDecimal treatmentCost(Appointment appointment) {
        BigDecimal full = appointment.treatmentCost();
        BigDecimal keep = BigDecimal.valueOf(100).subtract(TREATMENT_DISCOUNT_PERCENT);
        return full.multiply(keep).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal consultationFee(Appointment appointment) {
        return appointment.consultationFee();
    }
}
