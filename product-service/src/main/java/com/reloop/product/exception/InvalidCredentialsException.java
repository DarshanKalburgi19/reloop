package com.reloop.product.exception;

/**
 * Wrong e-mail or wrong password. One message for both cases so the login endpoint does not
 * reveal which e-mails are registered.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
