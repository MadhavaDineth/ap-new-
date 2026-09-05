package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;

/**
 * The everyday rule: the cost of the treatment plus the consultation fee of
 * the dentist who saw the patient. This is what the booking screen quotes,
 * so what the patient is told is what the patient pays.
 */
public class StandardPricing implements PricingStrategy {

    @Override
    public String name() {
        return "standard";
    }

    @Override
    public BigDecimal treatmentCost(Appointment appointment) {
        return appointment.treatmentCost();
    }

    @Override
    public BigDecimal consultationFee(Appointment appointment) {
        return appointment.consultationFee();
    }
}
