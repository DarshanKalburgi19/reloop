package com.reloop.product.exception;

/** Thrown when a registration uses an e-mail that already belongs to any account (either role). */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email already exists");
    }
}
