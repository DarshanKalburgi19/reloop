package com.reloop.repair.exception;

import com.reloop.repair.enums.RepairStatus;

/** The action isn't allowed in the request's current status (409). */
public class RepairStateException extends RuntimeException {

    private final RepairStatus currentStatus;

    public RepairStateException(RepairStatus currentStatus, String message) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public RepairStatus getCurrentStatus() {
        return currentStatus;
    }
}
