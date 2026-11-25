package com.reloop.repair.exception;

/** The profile can't be changed in its current status (e.g. SUSPENDED). Mapped to 409. */
public class ProfileStateException extends RuntimeException {

    public ProfileStateException(String message) {
        super(message);
    }
}
