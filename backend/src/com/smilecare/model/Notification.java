package com.smilecare.model;

import org.json.JSONObject;

/**
 * One confirmation, reminder, cancellation notice or receipt e-mail the
 * system has sent. Written by {@link com.smilecare.service.EmailListener}
 * for automatic sends, and by the manual "Send reminder" button.
 */
public record Notification(int id,
                           String appointmentNo,
                           String recipient,
                           String type,
                           String subject,
                           String message,
                           String sentAt) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("appointmentNo", appointmentNo)
            .put("recipient", recipient == null ? "clinic" : recipient)
            .put("type", type)
            .put("subject", subject)
            .put("message", message)
            .put("sentAt", sentAt);
    }
}
