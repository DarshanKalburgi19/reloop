package com.reloop.repair.exception;

/**
 * Unknown repair request, or one the caller isn't part of (customers see only their own requests,
 * repairers only requests assigned to their workshop). Both look the same: 404.
 */
public class RepairRequestNotFoundException extends RuntimeException {

    public RepairRequestNotFoundException(Long id) {
        super("Repair request not found: " + id);
    }
}
