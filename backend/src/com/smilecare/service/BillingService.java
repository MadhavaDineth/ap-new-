package com.smilecare.service;

import com.smilecare.config.AppConfig;
import com.smilecare.db.DaoFactory;
import com.smilecare.model.Appointment;
import com.smilecare.model.Bill;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Works out what a visit costs and writes the receipt.
 *
 * The arithmetic itself is delegated to a {@link PricingStrategy}, chosen once
 * from config.properties, so the clinic can run a promotion without this class
 * being touched.
 */
public class BillingService {

    private final AppointmentService appointments;
    private final PricingStrategy pricing;

    public BillingService(AppointmentService appointments) {
        this.appointments = appointments;
        this.pricing = chooseStrategy();
        System.out.println("[billing] pricing rule: " + pricing.name());
    }

    /** Reads pricing.strategy from config and builds the matching strategy. */
    private static PricingStrategy chooseStrategy() {
        AppConfig cfg = AppConfig.get();
        String wanted = cfg.value("pricing.strategy", "standard").toLowerCase();

        if (wanted.equals("promotional")) {
            BigDecimal percent = new BigDecimal(cfg.value("pricing.discount", "10"));
            return new PromotionalPricing(percent);
        }
        return new StandardPricing();
    }

    public Optional<Bill> find(String appointmentNo) {
        try {
            return DaoFactory.bills().findByAppointmentNo(appointmentNo.toUpperCase());
        } catch (SQLException e) {
            throw new AppException(500, "The bill could not be read: " + e.getMessage());
        }
    }

    public Bill require(String appointmentNo) {
        return find(appointmentNo).orElseThrow(() -> AppException.notFound(
            "No bill has been issued for " + appointmentNo.toUpperCase() + " yet."));
    }

    /**
     * Issues the receipt and completes the visit, using the pricing strategy
     * chosen on the billing screen ("standard", "senior", "insurance" or
     * "loyalty"). Leaving it null or blank falls back to whatever is
     * configured in config.properties, which keeps the config-driven demo of
     * the Strategy pattern working unchanged.
     *
     * Asking twice is harmless: the receipt that already exists is returned
     * rather than a second one being written, regardless of which key is
     * passed the second time.
     */
    public Bill issue(String appointmentNo, String pricingKey, Integer byUserId) {
        Appointment appt = appointments.require(appointmentNo);

        if (Appointment.CANCELLED.equals(appt.status())) {
            throw AppException.conflict(
                "This appointment was cancelled, so it cannot be billed.");
        }

        Optional<Bill> already = find(appt.appointmentNo());
        if (already.isPresent()) {
            return already.get();
        }

        PricingStrategy chosen = PricingStrategies.byKey(pricingKey, pricing);
        BigDecimal treatmentCost = chosen.treatmentCost(appt);
        BigDecimal consultationFee = chosen.consultationFee(appt);

        try {
            Bill bill = DaoFactory.bills().insertAndComplete(
                appt, DaoFactory.bills().nextNumber(),
                treatmentCost, consultationFee, PricingStrategies.keyFor(chosen), byUserId);

            appointments.listeners().forEach(l -> l.onBillIssued(bill, byUserId));
            return bill;

        } catch (SQLException e) {
            throw new AppException(500, "The bill could not be issued: " + e.getMessage());
        }
    }
}
