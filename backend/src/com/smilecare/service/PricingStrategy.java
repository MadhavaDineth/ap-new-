package com.smilecare.service;

import com.smilecare.model.Appointment;

import java.math.BigDecimal;

/**
 * Design pattern: STRATEGY.
 *
 * How a visit is priced is a rule that changes from time to time: a normal
 * visit pays the full consultation fee, a follow-up may not, a promotion may
 * take a percentage off. Each rule is a separate class implementing this
 * interface, and BillingService is handed whichever one is configured. Adding
 * a new rule never means editing the billing code.
 */
public interface PricingStrategy {

    /** Short name printed in the log and stored with the receipt. */
    String name();

    /** What the treatment itself costs. */
    BigDecimal treatmentCost(Appointment appointment);

    /** What the dentist charges for seeing the patient. */
    BigDecimal consultationFee(Appointment appointment);

    /** The two added together. Overriding is rarely needed. */
    default BigDecimal total(Appointment appointment) {
        return treatmentCost(appointment).add(consultationFee(appointment));
    }
}
