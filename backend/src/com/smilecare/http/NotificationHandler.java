package com.smilecare.http;

import com.smilecare.model.Notification;
import com.smilecare.model.User;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.smilecare.service.NotificationService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * GET  /api/notifications           the reminders log, newest first
 * POST /api/notifications/remind    send (log) a manual reminder
 *
 * Every address here needs a signed-in member of staff.
 */
public class NotificationHandler extends ApiHandler {

    public static final String PATH = "/api/notifications";

    private final NotificationService notifications;

    public NotificationHandler(AuthService auth, NotificationService notifications) {
        super(auth);
        this.notifications = notifications;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        User staff = requireUser(ex);
        String tail = Http.tail(ex, PATH).toLowerCase();
        String method = ex.getRequestMethod().toUpperCase();

        if (tail.isEmpty() && method.equals("GET")) {
            int limit = intParam(ex, "limit", 50);
            JSONArray out = new JSONArray();
            notifications.recent(limit).forEach(n -> out.put(n.toJson()));
            Http.json(ex, 200, out);
            return;
        }

        if (tail.equals("remind") && method.equals("POST")) {
            JSONObject in = Http.jsonBody(ex);
            String appointmentNo = in.optString("appointmentNo", "").trim();
            if (appointmentNo.isEmpty()) {
                throw AppException.badRequest("Which appointment is this reminder for?");
            }
            Notification sent = notifications.sendReminder(appointmentNo.toUpperCase(), staff.id());
            Http.json(ex, 201, sent.toJson());
            return;
        }

        methodNotAllowed(ex);
    }

    private static int intParam(HttpExchange ex, String name, int fallback) {
        String raw = Http.query(ex).get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw AppException.badRequest(name + " must be a whole number.");
        }
    }
}
