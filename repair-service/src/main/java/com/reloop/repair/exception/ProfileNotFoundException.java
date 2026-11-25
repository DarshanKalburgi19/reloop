package com.reloop.repair.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(Long id) {
        super("Repairer profile " + id + " not found");
    }
}
