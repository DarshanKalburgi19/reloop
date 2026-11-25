package com.reloop.repair.exception;

/** {@code POST /api/repairers} by a repairer who already has a profile. Mapped to 409 with {@code profileId}. */
public class WorkshopProfileExistsException extends RuntimeException {

    private final Long profileId;

    public WorkshopProfileExistsException(Long profileId) {
        super("You already have a workshop profile (id " + profileId + "). Use PUT /api/repairers/" + profileId
                + " to edit it.");
        this.profileId = profileId;
    }

    public Long getProfileId() {
        return profileId;
    }
}
