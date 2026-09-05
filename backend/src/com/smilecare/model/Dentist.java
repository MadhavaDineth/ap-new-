package com.smilecare.model;

import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * Data Transfer Object for a row of the dentists table.
 * Records are immutable, so a DTO cannot be changed by accident once a DAO
 * has built it.
 */
public record Dentist(int id,
                      String name,
                      String qualification,
                      String speciality,
                      BigDecimal consultationFee,
                      boolean active) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("qualification", qualification == null ? "" : qualification)
            .put("speciality", speciality == null ? "" : speciality)
            .put("consultationFee", consultationFee)
            .put("active", active);
    }
}
