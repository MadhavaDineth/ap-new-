package com.smilecare.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves index.html and the css/js folders, so the whole system can run from
 * http://localhost:8080 without WAMP and without any CORS settings at all.
 *
 * Only files inside the configured web root are served: a request containing
 * ".." is resolved and then checked against the root, so it cannot escape.
 */
public class StaticHandler implements HttpHandler {

    private final Path root;

    public StaticHandler(String webRoot) {
        this.root = Path.of(webRoot).toAbsolutePath().normalize();
        System.out.println("[web] serving files from " + root);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            Http.send(ex, 405, "text/plain", "Method not allowed".getBytes());
            return;
        }
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        Path file = root.resolve(path.substring(1)).normalize();

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            Http.send(ex, 404, "text/html; charset=utf-8",
                ("<h1>404</h1><p>" + path + " is not on this server.</p>").getBytes());
            return;
        }

        byte[] payload = Files.readAllBytes(file);
        Http.send(ex, 200, contentType(file), payload);
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css"))  return "text/css; charset=utf-8";
        if (name.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg"))  return "image/svg+xml";
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".ico"))  return "image/x-icon";
        if (name.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
