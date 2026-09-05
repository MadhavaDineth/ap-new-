package com.smilecare.http;

import com.smilecare.model.Appointment;
import com.smilecare.model.Patient;
import com.smilecare.model.User;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.smilecare.service.PatientService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * GET    /api/patients            list, optionally filtered by ?q=
 * GET    /api/patients/{id}       one patient
 * GET    /api/patients/{id}/history   every visit they have had
 * POST   /api/patients            add a new patient
 * PUT    /api/patients/{id}       edit name, address or contact number
 * DELETE /api/patients/{id}       remove, only when they have no appointments
 *
 * Any signed-in member of staff may manage patients - this is front desk
 * work, not restricted to administrators.
 */
public class PatientHandler extends ApiHandler {

    public static final String PATH = "/api/patients";

    private final PatientService patients;

    public PatientHandler(AuthService auth, PatientService patients) {
        super(auth);
        this.patients = patients;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        User staff = requireUser(ex);
        String tail = Http.tail(ex, PATH);
        String method = ex.getRequestMethod().toUpperCase();

        int slash = tail.indexOf('/');
        String idPart = slash < 0 ? tail : tail.substring(0, slash);
        boolean history = slash >= 0 && tail.substring(slash + 1).equalsIgnoreCase("history");

        if (idPart.isEmpty()) {
            switch (method) {
                case "GET"  -> list(ex);
                case "POST" -> create(ex, staff);
                default -> methodNotAllowed(ex);
            }
            return;
        }

        int id = id(idPart);
        if (history) {
            if (!method.equals("GET")) {
                methodNotAllowed(ex);
                return;
            }
            JSONArray out = new JSONArray();
            patients.history(id).forEach(a -> out.put(a.toJson()));
            Http.json(ex, 200, out);
            return;
        }

        switch (method) {
            case "GET"    -> Http.json(ex, 200, patients.require(id).toJson());
            case "PUT"    -> update(ex, staff, id);
            case "DELETE" -> delete(ex, staff, id);
            default -> methodNotAllowed(ex);
        }
    }

    private void list(HttpExchange ex) throws IOException {
        String q = Http.query(ex).get("q");
        JSONArray out = new JSONArray();
        patients.search(q).forEach(p -> out.put(p.toJson()));
        Http.json(ex, 200, out);
    }

    private void create(HttpExchange ex, User staff) throws IOException {
        JSONObject in = Http.jsonBody(ex);
        Patient saved = patients.create(staff.id(),
            in.optString("name", ""), in.optString("address", ""), in.optString("contactNo", ""));
        Http.json(ex, 201, saved.toJson());
    }

    private void update(HttpExchange ex, User staff, int id) throws IOException {
        JSONObject in = Http.jsonBody(ex);
        Patient saved = patients.update(staff.id(), id,
            in.optString("name", ""), in.optString("address", ""), in.optString("contactNo", ""));
        Http.json(ex, 200, saved.toJson());
    }

    private void delete(HttpExchange ex, User staff, int id) throws IOException {
        patients.delete(staff.id(), id);
        Http.json(ex, 200, new JSONObject().put("ok", true));
    }

    private static int id(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw AppException.badRequest("\"" + raw + "\" is not a patient number.");
        }
    }
}
