package com.smilecare.http;

import com.smilecare.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.IOException;

/** POST /api/auth/login and POST /api/auth/logout */
public class AuthHandler extends ApiHandler {

    public static final String PATH = "/api/auth/";

    public AuthHandler(AuthService auth) {
        super(auth);
    }

    @Override
    protected void route(HttpExchange ex) throws IOException {
        String action = Http.tail(ex, PATH);

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            methodNotAllowed(ex);
            return;
        }

        switch (action) {
            case "login" -> login(ex);
            case "logout" -> logout(ex);
            default -> Http.error(ex, 404, "Unknown address: " + ex.getRequestURI().getPath());
        }
    }

    private void login(HttpExchange ex) throws IOException {
        JSONObject in = Http.jsonBody(ex);
        AuthService.Session session = auth.login(
            in.optString("username", ""), in.optString("password", ""));

        JSONObject out = session.user().toJson();
        out.put("token", session.token());
        Http.json(ex, 200, out);
    }

    private void logout(HttpExchange ex) throws IOException {
        auth.logout(Http.bearer(ex));
        Http.json(ex, 200, new JSONObject().put("ok", true));
    }
}
