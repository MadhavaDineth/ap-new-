package com.smilecare.model;

import org.json.JSONObject;

/** Data Transfer Object for a row of the patients table. */
public record Patient(int id,
                      String name,
                      String address,
                      String contactNo) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("address", address)
            .put("contactNo", contactNo);
    }
}
