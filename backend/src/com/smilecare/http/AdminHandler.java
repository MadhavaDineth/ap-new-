package com.smilecare.http;

import com.smilecare.model.AuditEntry;
import com.smilecare.model.Dentist;
import com.smilecare.model.Treatment;
import com.smilecare.model.User;
import com.smilecare.service.AdminService;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * The admin panel.
 *
 *   GET  /api/admin/dentists        POST /api/admin/dentists     PUT /api/admin/dentists/{id}
 *   GET  /api/admin/treatments      POST /api/admin/treatments   PUT /api/admin/treatments/{id}
 *   GET  /api/admin/users           POST /api/admin/users        PUT /api/admin/users/{id}
 *   GET  /api/admin/audit?limit=50
 *
 * Every address here is behind requireAdmin, so a receptionist who types the
 * URL by hand gets a 403 rather than the price list.
 */
public class AdminHandler extends ApiHandler {

    public static final String PATH = "/api/admin";

    private final AdminService admin;

    public AdminHandler(AuthService auth, AdminService admin) {
        super(auth);
        this.admin = admin;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        User staff = requireAdmin(ex);

        String tail = Http.tail(ex, PATH);            // dentists, or dentists/3
        String method = ex.getRequestMethod().toUpperCase();

        int slash = tail.indexOf('/');
        String what = (slash < 0 ? tail : tail.substring(0, slash)).toLowerCase();
        String idPart = slash < 0 ? "" : tail.substring(slash + 1);

        switch (what) {
            case "dentists"   -> dentists(ex, staff, method, idPart);
            case "treatments" -> treatments(ex, staff, method, idPart);
            case "users"      -> users(ex, staff, method, idPart);
            case "audit"      -> auditLog(ex, method);
            default -> throw AppException.notFound("There is no admin section called \"" + what + "\".");
        }
    }

    /* ---------------------------------------------------------------- dentists */

    private void dentists(HttpExchange ex, User staff, String method, String idPart)
            throws IOException {
        if (method.equals("GET") && idPart.isEmpty()) {
            Http.json(ex, 200, arrayOf(admin.dentists(), Dentist::toJson));

        } else if (method.equals("POST") && idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            Dentist saved = admin.createDentist(
                staff.id(),
                in.optString("name", ""),
                in.optString("qualification", ""),
                in.optString("speciality", ""),
                money(in, "consultationFee"));
            Http.json(ex, 201, saved.toJson());

        } else if (method.equals("PUT") && !idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            Dentist saved = admin.updateDentist(
                staff.id(),
                id(idPart),
                in.optString("name", ""),
                in.optString("qualification", ""),
                in.optString("speciality", ""),
                money(in, "consultationFee"),
                in.optBoolean("active", true));
            Http.json(ex, 200, saved.toJson());

        } else {
            methodNotAllowed(ex);
        }
    }

    /* -------------------------------------------------------------- treatments */

    private void treatments(HttpExchange ex, User staff, String method, String idPart)
            throws IOException {
        if (method.equals("GET") && idPart.isEmpty()) {
            Http.json(ex, 200, arrayOf(admin.treatments(), Treatment::toJson));

        } else if (method.equals("POST") && idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            Treatment saved = admin.createTreatment(
                staff.id(),
                in.optString("treatmentType", ""),
                money(in, "baseCost"),
                in.optInt("durationMins", 30));
            Http.json(ex, 201, saved.toJson());

        } else if (method.equals("PUT") && !idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            Treatment saved = admin.updateTreatment(
                staff.id(),
                id(idPart),
                in.optString("treatmentType", ""),
                money(in, "baseCost"),
                in.optInt("durationMins", 30),
                in.optBoolean("active", true));
            Http.json(ex, 200, saved.toJson());

        } else {
            methodNotAllowed(ex);
        }
    }

    /* ------------------------------------------------------------------- users */

    private void users(HttpExchange ex, User staff, String method, String idPart)
            throws IOException {
        if (method.equals("GET") && idPart.isEmpty()) {
            Http.json(ex, 200, arrayOf(admin.users(), User::toJson));

        } else if (method.equals("POST") && idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            User saved = admin.createUser(
                staff.id(),
                in.optString("username", ""),
                in.optString("fullName", ""),
                in.optString("role", "receptionist"),
                in.optString("password", ""));
            Http.json(ex, 201, saved.toJson());

        } else if (method.equals("PUT") && !idPart.isEmpty()) {
            JSONObject in = Http.jsonBody(ex);
            User saved = admin.updateUser(
                staff.id(),
                id(idPart),
                in.optString("fullName", ""),
                in.optString("role", "receptionist"),
                in.optBoolean("active", true),
                in.optString("password", ""));
            Http.json(ex, 200, saved.toJson());

        } else {
            methodNotAllowed(ex);
        }
    }

    /* ------------------------------------------------------------------- audit */

    private void auditLog(HttpExchange ex, String method) throws IOException {
        if (!method.equals("GET")) {
            methodNotAllowed(ex);
            return;
        }
        String raw = Http.query(ex).get("limit");
        int limit = 50;
        if (raw != null) {
            try {
                limit = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw AppException.badRequest("limit must be a whole number.");
            }
        }
        Http.json(ex, 200, arrayOf(admin.audit(limit), AuditEntry::toJson));
    }

    /* ----------------------------------------------------------------- helpers */

    private static <T> JSONArray arrayOf(List<T> rows, Function<T, Object> toJson) {
        JSONArray out = new JSONArray();
        rows.forEach(row -> out.put(toJson.apply(row)));
        return out;
    }

    private static int id(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw AppException.badRequest("\"" + raw + "\" is not a record number.");
        }
    }

    /** Money arrives as a JSON number or a string; both become a BigDecimal. */
    private static BigDecimal money(JSONObject in, String field) {
        Object raw = in.opt(field);
        if (raw == null) {
            throw AppException.badRequest("Enter the amount for " + field + ".");
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw AppException.badRequest("\"" + raw + "\" is not an amount.");
        }
    }
}
