package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A second strategy, used when the clinic runs a promotion: a percentage is
 * taken off the treatment charge while the consultation fee stays as it is.
 *
 * Switch to it without touching any other class by setting
 *     pricing.strategy=promotional
 *     pricing.discount=10
 * in config.properties.
 */
public class PromotionalPricing implements PricingStrategy {

    private final BigDecimal percentOff;

    public PromotionalPricing(BigDecimal percentOff) {
        if (percentOff.signum() < 0 || percentOff.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("A discount must be between 0 and 100.");
        }
        this.percentOff = percentOff;
    }

    @Override
    public String name() {
        return "promotional " + percentOff.stripTrailingZeros().toPlainString() + "%";
    }

    @Override
    public BigDecimal treatmentCost(Appointment appointment) {
        BigDecimal full = appointment.treatmentCost();
        BigDecimal keep = BigDecimal.valueOf(100).subtract(percentOff);
        return full.multiply(keep)
                   .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal consultationFee(Appointment appointment) {
        return appointment.consultationFee();
    }
}
