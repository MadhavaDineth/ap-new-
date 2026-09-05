package com.smilecare.service;

import com.smilecare.db.DaoFactory;
import com.smilecare.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Signing staff in and out.
 *
 * Passwords are stored as SHA-256 hex, the same way MySQL's SHA2(pw, 256)
 * writes them, so the seed data in database.sql works straight away and the
 * plain password is never kept anywhere.
 *
 * Sessions live in memory: a random token is handed to the browser and looked
 * up on later requests. Restarting the server signs everybody out, which is
 * the safe default for a clinic front desk.
 */
public class AuthService {

    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }

    /** @return the signed-in user and the token to send back to the browser */
    public Session login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw AppException.badRequest("Enter both a username and a password.");
        }

        Optional<User> found;
        try {
            found = DaoFactory.users()
                .authenticate(username.trim().toLowerCase(), sha256(password));
        } catch (SQLException e) {
            throw new AppException(500, "The user database could not be read: " + e.getMessage());
        }

        User user = found.orElseThrow(
            () -> AppException.unauthorised("Username or password is incorrect."));

        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user);
        System.out.println("[auth] " + user.username() + " signed in");
        return new Session(user, token);
    }

    public void logout(String token) {
        if (token != null) {
            User gone = sessions.remove(token);
            if (gone != null) {
                System.out.println("[auth] " + gone.username() + " signed out");
            }
        }
    }

    /** The user behind a bearer token, or empty when the token is unknown. */
    public Optional<User> userFor(String token) {
        return Optional.ofNullable(token == null ? null : sessions.get(token));
    }

    /** Used by handlers that must not run for an anonymous visitor. */
    public User require(String token) {
        return userFor(token).orElseThrow(
            () -> AppException.unauthorised("Please sign in again."));
    }

    /** A signed-in user together with the token issued for that session. */
    public record Session(User user, String token) { }
}
