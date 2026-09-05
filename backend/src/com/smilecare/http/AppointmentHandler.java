package com.smilecare.http;

import com.smilecare.model.Appointment;
import com.smilecare.service.AppException;
import com.smilecare.service.AppointmentService;
import com.smilecare.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * GET  /api/appointments            the diary, filterable
 * POST /api/appointments            register a new one
 * GET  /api/appointments/{no}       one record
 * PUT  /api/appointments/{no}       change the status
 *
 * Reading a single appointment is public, because a patient checks their own
 * booking from the home page. Everything else needs a signed-in staff member.
 */
public class AppointmentHandler extends ApiHandler {

    public static final String PATH = "/api/appointments";

    private final AppointmentService service;

    public AppointmentHandler(AuthService auth, AppointmentService service) {
        super(auth);
        this.service = service;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        String number = Http.tail(ex, PATH);
        String method = ex.getRequestMethod().toUpperCase();

        if (number.isEmpty()) {
            switch (method) {
                case "GET" -> list(ex);
                case "POST" -> create(ex);
                default -> methodNotAllowed(ex);
            }
        } else {
            switch (method) {
                case "GET" -> one(ex, number);
                case "PUT", "PATCH" -> changeStatus(ex, number);
                default -> methodNotAllowed(ex);
            }
        }
    }

    /* ---------------- the diary ---------------- */
    private void list(HttpExchange ex) throws IOException {
        requireUser(ex);

        Map<String, String> q = Http.query(ex);
        LocalDate date = parseDate(q.get("date"));
        String status = q.get("status");
        if (status != null && !Appointment.isValidStatus(status)) {
            throw AppException.badRequest("Unknown status: " + status);
        }

        JSONArray out = new JSONArray();
        service.list(date, status, q.get("q")).forEach(a -> out.put(a.toJson()));
        Http.json(ex, 200, out);
    }

    /* ---------------- one record ---------------- */
    private void one(HttpExchange ex, String number) throws IOException {
        Http.json(ex, 200, service.require(number.toUpperCase()).toJson());
    }

    /* ---------------- register ---------------- */
    private void create(HttpExchange ex) throws IOException {
        // A patient books from the public website without an account, so this
        // is deliberately open. When a member of staff books at the front desk
        // their id is picked up from the token and stored as created_by.
        Integer userId = optionalUserId(ex);
        JSONObject in = Http.jsonBody(ex);

        Appointment saved = service.create(
            in.optString("patientName", null),
            in.optString("address", null),
            in.optString("contactNo", null),
            in.optInt("dentistId", 0),
            in.optInt("treatmentId", 0),
            requireDate(in.optString("date", null)),
            requireTime(in.optString("time", null)),
            userId);

        Http.json(ex, 201, saved.toJson());
    }

    /* ---------------- status ---------------- */
    private void changeStatus(HttpExchange ex, String number) throws IOException {
        Integer userId = requireUser(ex).id();
        JSONObject in = Http.jsonBody(ex);
        String status = in.optString("status", "").trim();

        Appointment updated = service.changeStatus(number.toUpperCase(), status, userId);
        Http.json(ex, 200, updated.toJson());
    }

    /* ---------------- parsing ---------------- */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw AppException.badRequest("The date must look like 2026-09-10.");
        }
    }

    private static LocalDate requireDate(String value) {
        LocalDate date = parseDate(value);
        if (date == null) {
            throw AppException.badRequest("Please choose a date.");
        }
        return date;
    }

    private static LocalTime requireTime(String value) {
        if (value == null || value.isBlank()) {
            throw AppException.badRequest("Please choose a time.");
        }
        try {
            return LocalTime.parse(value.length() == 5 ? value : value.substring(0, 5));
        } catch (RuntimeException e) {
            throw AppException.badRequest("The time must look like 10:30.");
        }
    }
}
