package com.smilecare.http;

import com.smilecare.model.Report;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.smilecare.service.ReportService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * GET /api/reports/summary       the four figures at the top
 * GET /api/reports/revenue       revenue per day        ?days=14
 * GET /api/reports/appointments  appointments per day   ?days=14
 * GET /api/reports/treatments    most booked treatments ?limit=6
 * GET /api/reports/status        the status breakdown
 * GET /api/reports/workload      appointments per dentist
 *
 * Reports show what the clinic earns, so every one of them needs a signed-in
 * member of staff. They are all reads: nothing here changes the database.
 */
public class ReportHandler extends ApiHandler {

    public static final String PATH = "/api/reports";

    private final ReportService reports;

    public ReportHandler(AuthService auth, ReportService reports) {
        super(auth);
        this.reports = reports;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            methodNotAllowed(ex);
            return;
        }
        requireUser(ex);

        String what = Http.tail(ex, PATH).toLowerCase();
        int days = intParam(ex, "days", 14);

        switch (what) {
            case "summary" ->
                Http.json(ex, 200, reports.summary().toJson());

            case "revenue" -> {
                Map<String, String> q = Http.query(ex);
                if (q.get("from") != null && q.get("to") != null) {
                    Http.json(ex, 200, arrayOf(
                        reports.revenueRange(parseDate(q.get("from")), parseDate(q.get("to"))),
                        Report.DayRevenue::toJson));
                } else {
                    Http.json(ex, 200, arrayOf(reports.revenue(days), Report.DayRevenue::toJson));
                }
            }

            case "appointments" ->
                Http.json(ex, 200, arrayOf(reports.appointmentsPerDay(days), Report.DayCount::toJson));

            case "treatments" ->
                Http.json(ex, 200, arrayOf(reports.topTreatments(intParam(ex, "limit", 6)),
                                           Report.TreatmentCount::toJson));

            case "patients" ->
                Http.json(ex, 200, arrayOf(reports.topPatients(intParam(ex, "limit", 10)),
                                           Report.PatientCount::toJson));

            case "status" ->
                Http.json(ex, 200, reports.statusMix().toJson());

            case "workload" ->
                Http.json(ex, 200, arrayOf(reports.dentistWorkload(), Report.Workload::toJson));

            default ->
                throw AppException.notFound("There is no report called \"" + what + "\".");
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw AppException.badRequest("Dates must look like 2026-09-10.");
        }
    }

    /** Turns a list of DTOs into a JSON array without repeating the loop six times. */
    private static <T> JSONArray arrayOf(List<T> rows, Function<T, Object> toJson) {
        JSONArray out = new JSONArray();
        rows.forEach(row -> out.put(toJson.apply(row)));
        return out;
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
