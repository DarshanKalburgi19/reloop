package com.reloop.repair.exception;

/** A repairer tried to change someone else's workshop profile. Mapped to 403. */
public class ProfileOwnershipException extends RuntimeException {

    public ProfileOwnershipException() {
        super("You can only edit your own workshop profile");
    }
}
