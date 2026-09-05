package com.smilecare.db;

import com.smilecare.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * The single point where JDBC connections are made.
 *
 * Design pattern: SINGLETON. One object owns the database settings, so the
 * driver is registered once and every DAO borrows connections the same way.
 *
 * Each call to {@link #open()} returns a fresh short lived connection which
 * the caller closes with try-with-resources. That keeps the code simple and
 * avoids one shared connection being used by two request threads at once.
 */
public final class DbConnection {

    private static DbConnection instance;

    private final String url;
    private final String user;
    private final String password;

    private DbConnection() {
        AppConfig cfg = AppConfig.get();
        this.url = cfg.dbUrl();
        this.user = cfg.dbUser();
        this.password = cfg.dbPassword();

        try {
            // not strictly needed since JDBC 4, but makes a missing jar obvious
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "MySQL Connector/J is not on the classpath. Check backend/lib.", e);
        }
    }

    public static synchronized DbConnection get() {
        if (instance == null) {
            instance = new DbConnection();
        }
        return instance;
    }

    /** A new connection. Always use it inside try-with-resources. */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Called once at start-up so a wrong password fails loudly, not later. */
    public void verify() throws SQLException {
        try (Connection c = open()) {
            System.out.println("[db] connected to " + c.getCatalog()
                + " as " + user + " (" + c.getMetaData().getDatabaseProductVersion() + ")");
        }
    }
}
