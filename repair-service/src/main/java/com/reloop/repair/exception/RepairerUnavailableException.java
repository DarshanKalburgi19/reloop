package com.reloop.repair.exception;

/** The chosen workshop exists but can't take this request right now (409). */
public class RepairerUnavailableException extends RuntimeException {

    public RepairerUnavailableException(String message) {
        super(message);
    }
}
