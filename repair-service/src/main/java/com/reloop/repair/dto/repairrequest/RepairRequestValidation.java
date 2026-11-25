package com.reloop.repair.dto.repairrequest;

/** Shared limits and patterns for repair request payloads. */
public final class RepairRequestValidation {

    /** No file hosting yet: images are bundled demo images or external https URLs. */
    public static final String IMAGE_URL = "^(/demo-images/[A-Za-z0-9][A-Za-z0-9/_.-]*|https://[^\\s]+)$";
    public static final String IMAGE_URL_MESSAGE = "must be a /demo-images/... path or an https:// URL";
    public static final int MAX_IMAGES = 5;
    public static final int MAX_NOTE = 1000;

    private RepairRequestValidation() {
    }
}
