package com.reloop.repair.exception;

/** The part doesn't exist or belongs to another repairer (both are 404, so IDs can't be probed). */
public class SparePartNotFoundException extends RuntimeException {

    public SparePartNotFoundException(Long id) {
        super("Spare part not found: " + id);
    }
}
