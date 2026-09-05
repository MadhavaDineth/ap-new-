package com.smilecare.model;

import org.json.JSONObject;

import java.math.BigDecimal;

/** Data Transfer Object for a row of the treatments table. */
public record Treatment(int id,
                        String treatmentType,
                        BigDecimal baseCost,
                        int durationMins,
                        boolean active) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("treatmentType", treatmentType)
            .put("baseCost", baseCost)
            .put("durationMins", durationMins)
            .put("active", active);
    }
}
