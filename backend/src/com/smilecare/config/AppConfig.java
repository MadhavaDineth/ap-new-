package com.smilecare.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application settings, loaded once from config.properties.
 *
 * Design pattern: SINGLETON. Only one instance can ever exist, so every part
 * of the program reads exactly the same settings.
 */
public final class AppConfig {

    private static AppConfig instance;

    private final Properties props = new Properties();

    private AppConfig() {
        // look for the file next to the running program first, then on the
        // classpath, so it works from both run.bat and an IDE
        Path file = Path.of("config.properties");
        if (!Files.exists(file)) {
            file = Path.of("backend", "config.properties");
        }

        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
                System.out.println("[config] loaded " + file.toAbsolutePath());
            } else {
                System.out.println("[config] config.properties not found, using defaults");
            }
        } catch (IOException e) {
            throw new IllegalStateException("config.properties could not be read", e);
        }
    }

    /** Returns the one and only instance, creating it on first use. */
    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public String value(String key, String fallback) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    public int intValue(String key, int fallback) {
        try {
            return Integer.parseInt(value(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public String dbUrl()      { return value("db.url", "jdbc:mysql://localhost:3306/smilecare"); }
    public String dbUser()     { return value("db.user", "root"); }
    public String dbPassword() { return props.getProperty("db.password", ""); }
    public int    port()       { return intValue("server.port", 8080); }
    public String webRoot()    { return value("web.root", ".."); }
    public String corsOrigin() { return value("cors.origin", "http://localhost"); }
}
