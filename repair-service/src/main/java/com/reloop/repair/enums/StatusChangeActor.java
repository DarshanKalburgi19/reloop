package com.reloop.repair.enums;

/**
 * StatusChangeActor - fixed status/choice enum (stored as string, never ordinal).
 *
 * <p>Who changed a repair status - drives dashboard permissions.</p>
 */
public enum StatusChangeActor {

    CUSTOMER,
    REPAIRER,
    SYSTEM

}
