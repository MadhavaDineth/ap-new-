package com.smilecare.model;

import org.json.JSONObject;

/**
 * A signed-in staff member. The password hash is deliberately not part of
 * this DTO, so it can never be sent to the browser by mistake.
 */
public record User(int id,
                   String username,
                   String fullName,
                   String role,
                   boolean active) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("username", username)
            .put("fullName", fullName)
            .put("role", role.toLowerCase())
            .put("active", active);
    }
}
