package com.reloop.repair.enums;

/** How a service price should be read: an exact price or a "starting from" price. */
public enum PriceType {

    /** Exact price, e.g. "Screen guard fitting - ₹199". */
    FIXED,
    /** Minimum price; the final quote depends on inspection, e.g. "From ₹1,499". */
    STARTING_FROM

}
