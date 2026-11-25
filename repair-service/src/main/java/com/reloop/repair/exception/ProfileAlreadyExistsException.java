package com.reloop.repair.exception;

/** An account can own at most one repairer profile (uk_profiles_account). */
public class ProfileAlreadyExistsException extends RuntimeException {

    public ProfileAlreadyExistsException(Long accountExternalId) {
        super("A repairer profile already exists for account " + accountExternalId);
    }
}
