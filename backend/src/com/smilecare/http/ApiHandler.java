package com.smilecare.http;

import com.smilecare.model.User;
import com.smilecare.service.AppException;
import com.smilecare.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * The shape every API endpoint shares: CORS headers, the browser pre-flight,
 * and one place where an AppException becomes the right status code and a
 * readable message. Subclasses only write {@link #route}.
 *
 * This is the template method pattern: handle() fixes the steps, route()
 * fills in the part that differs.
 */
public abstract class ApiHandler implements HttpHandler {

    protected final AuthService auth;

    protected ApiHandler(AuthService auth) {
        this.auth = auth;
    }

    protected abstract void route(HttpExchange ex) throws IOException;

    @Override
    public final void handle(HttpExchange ex) throws IOException {
        Http.cors(ex);

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            Http.send(ex, 204, "text/plain", new byte[0]);
            return;
        }

        try {
            route(ex);
        } catch (AppException e) {
            Http.error(ex, e.status(), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            Http.error(ex, 500, "Something went wrong on the server. Please try again.");
        } finally {
            ex.close();
        }
    }

    /** The signed-in user, or a 401 for anyone else. */
    protected User requireUser(HttpExchange ex) {
        return auth.require(Http.bearer(ex));
    }

    /** The signed-in user id when there is one, otherwise null. */
    protected Integer optionalUserId(HttpExchange ex) {
        return auth.userFor(Http.bearer(ex)).map(User::id).orElse(null);
    }

    /** The signed-in user, but only if they are an administrator. */
    protected User requireAdmin(HttpExchange ex) {
        User user = requireUser(ex);
        if (!"admin".equalsIgnoreCase(user.role())) {
            throw new AppException(403, "Only an administrator can use this screen.");
        }
        return user;
    }

    protected void methodNotAllowed(HttpExchange ex) throws IOException {
        Http.error(ex, 405, ex.getRequestMethod() + " is not supported on this address.");
    }
}
