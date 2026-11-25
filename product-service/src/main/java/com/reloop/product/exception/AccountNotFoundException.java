package com.reloop.product.exception;

/** A valid token refers to an account that no longer exists. Mapped to 401. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Account for this token no longer exists");
    }
}
