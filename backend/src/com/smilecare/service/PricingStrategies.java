package com.smilecare.service;

/**
 * Looks up a {@link PricingStrategy} by the short key the billing screen
 * sends ("standard", "senior", "insurance", "loyalty"). This is what lets
 * the receptionist choose the rule per bill; the strategy configured in
 * config.properties remains only the fallback when no key is given, which
 * keeps the existing Strategy pattern demonstration working unchanged.
 */
public final class PricingStrategies {

    private PricingStrategies() { }

    public static PricingStrategy byKey(String key, PricingStrategy fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        return switch (key.trim().toLowerCase()) {
            case "standard" -> new StandardPricing();
            case "senior", "senior-citizen", "seniorcitizen" -> new SeniorCitizenPricing();
            case "insurance" -> new InsurancePricing();
            case "loyalty" -> new LoyaltyPricing();
            default -> throw AppException.badRequest(
                "\"" + key + "\" is not a pricing strategy. Choose standard, senior, insurance or loyalty.");
        };
    }

    /** The short key a strategy is known by, for storing alongside the bill. */
    public static String keyFor(PricingStrategy strategy) {
        if (strategy instanceof SeniorCitizenPricing) return "senior";
        if (strategy instanceof InsurancePricing) return "insurance";
        if (strategy instanceof LoyaltyPricing) return "loyalty";
        if (strategy instanceof StandardPricing) return "standard";
        return "promotional";
    }
}
