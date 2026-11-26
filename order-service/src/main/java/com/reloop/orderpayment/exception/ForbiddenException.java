package com.reloop.orderpayment.exception;

/** The caller is authenticated but may not see or change this resource (403). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
