package com.smilecare.http;

import com.smilecare.db.DaoFactory;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONArray;

import java.io.IOException;
import java.sql.SQLException;

/**
 * GET /api/dentists and GET /api/treatments
 *
 * These two lists fill the drop-downs on the booking screen and are also shown
 * on the public website, so they do not require a sign-in.
 */
public class ReferenceHandler extends ApiHandler {

    private final String what;

    public ReferenceHandler(AuthService auth, String what) {
        super(auth);
        this.what = what;
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            methodNotAllowed(ex);
            return;
        }

        JSONArray out = new JSONArray();
        try {
            if (what.equals("dentists")) {
                DaoFactory.dentists().findAll().forEach(d -> out.put(d.toJson()));
            } else {
                DaoFactory.treatments().findAll().forEach(t -> out.put(t.toJson()));
            }
        } catch (SQLException e) {
            throw new AppException(500, "The " + what + " list could not be read: " + e.getMessage());
        }

        Http.json(ex, 200, out);
    }
}
