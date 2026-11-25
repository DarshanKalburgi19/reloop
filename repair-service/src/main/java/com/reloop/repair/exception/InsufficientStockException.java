package com.reloop.repair.exception;

/** A movement would make a part's quantity negative. Mapped to 409 with the available and requested units. */
public class InsufficientStockException extends RuntimeException {

    private final Long sparePartId;
    private final int available;
    private final int requested;

    public InsufficientStockException(Long sparePartId, String partName, int available, int requested) {
        super("Not enough stock of '" + partName + "': " + available + " available, " + requested + " requested");
        this.sparePartId = sparePartId;
        this.available = available;
        this.requested = requested;
    }

    public Long getSparePartId() {
        return sparePartId;
    }

    public int getAvailable() {
        return available;
    }

    public int getRequested() {
        return requested;
    }
}
