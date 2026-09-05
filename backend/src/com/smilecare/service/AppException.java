package com.smilecare.service;

/**
 * A problem the user can do something about: a missing appointment, a taken
 * slot, a bad password. It carries the HTTP status the handler should send,
 * and a message written for the person at the front desk rather than for a
 * programmer, because the front end shows it word for word.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static AppException notFound(String message)  { return new AppException(404, message); }
    public static AppException conflict(String message)  { return new AppException(409, message); }
    public static AppException badRequest(String message) { return new AppException(400, message); }
    public static AppException unauthorised(String message) { return new AppException(401, message); }
}
