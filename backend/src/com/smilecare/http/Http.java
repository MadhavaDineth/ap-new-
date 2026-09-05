package com.smilecare.http;

import com.smilecare.config.AppConfig;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Small helpers so the handlers can stay about the clinic, not about plumbing. */
public final class Http {

    private Http() { }

    /* ---------------- reading the request ---------------- */

    public static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static JSONObject jsonBody(HttpExchange ex) throws IOException {
        String raw = body(ex);
        if (raw == null || raw.isBlank()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            throw new com.smilecare.service.AppException(400, "The request body is not valid JSON.");
        }
    }

    /** ?date=2026-09-10&status=Pending becomes a map. */
    public static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                if (!value.isBlank()) {
                    out.put(key, value);
                }
            }
        }
        return out;
    }

    /** The part after the context path, so /api/appointments/APT-1042 gives APT-1042. */
    public static String tail(HttpExchange ex, String contextPath) {
        String path = ex.getRequestURI().getPath();
        String tail = path.length() > contextPath.length() ? path.substring(contextPath.length()) : "";
        while (tail.startsWith("/")) {
            tail = tail.substring(1);
        }
        return URLDecoder.decode(tail, StandardCharsets.UTF_8);
    }

    /** The token from "Authorization: Bearer abc123", or null. */
    public static String bearer(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /* ---------------- writing the response ---------------- */

    public static void cors(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", AppConfig.get().corsOrigin());
        h.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, OPTIONS");
        h.set("Vary", "Origin");
    }

    public static void send(HttpExchange ex, int status, String contentType, byte[] payload)
            throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        if (payload.length == 0) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, payload.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(payload);
        }
    }

    public static void json(HttpExchange ex, int status, Object json) throws IOException {
        send(ex, status, "application/json; charset=utf-8",
             String.valueOf(json).getBytes(StandardCharsets.UTF_8));
    }

    public static void error(HttpExchange ex, int status, String message) throws IOException {
        json(ex, status, new JSONObject().put("message", message).put("status", status));
    }
}
