package com.reloop.product.exception;

/** Correct credentials, but the account is SUSPENDED or CLOSED. */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(String status) {
        super("Account is " + status + " and cannot sign in");
    }
}
