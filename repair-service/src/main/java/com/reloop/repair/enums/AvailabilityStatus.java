package com.reloop.repair.enums;

/** Whether a workshop is currently taking new repair requests (set by the repairer). */
public enum AvailabilityStatus {

    /** Accepting new requests. */
    AVAILABLE,
    /** Accepting requests, but expect longer turnaround. */
    BUSY,
    /** Temporarily not accepting new requests (e.g. on leave). Still visible in search. */
    UNAVAILABLE

}
