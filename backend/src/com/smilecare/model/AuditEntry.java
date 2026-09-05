package com.smilecare.model;

import org.json.JSONObject;

/**
 * One line of the audit trail, already joined to the name of the member of
 * staff who caused it. Entries are never edited, only read back, so this DTO
 * has no setters and no id beyond the one used for ordering.
 */
public record AuditEntry(int id,
                         String who,
                         String action,
                         String entity,
                         String reference,
                         String details,
                         String loggedAt) {

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("who", who)
            .put("action", action)
            .put("entity", entity)
            .put("reference", reference == null ? "" : reference)
            .put("details", details == null ? "" : details)
            .put("loggedAt", loggedAt);
    }
}
