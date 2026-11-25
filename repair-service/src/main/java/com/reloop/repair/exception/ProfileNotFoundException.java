package com.reloop.repair.exception;

public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(Long id) {
        super("Repairer profile " + id + " not found");
    }

    private ProfileNotFoundException(String message) {
        super(message);
    }

    /** {@code GET /api/repairers/me} for a repairer without a profile. */
    public static ProfileNotFoundException noOwnProfile() {
        return new ProfileNotFoundException("You don't have a workshop profile yet. Create one with POST /api/repairers.");
    }
}
