package com.reloop.repair.dto.repairer;

/** Shared validation patterns and limits for the workshop profile API. */
public final class RepairerValidation {

    public static final String PHONE = "^\\+?[0-9]{10,15}$";
    public static final String PINCODE = "^[1-9][0-9]{5}$";
    /** 24-hour "HH:mm", 00:00 to 23:59. */
    public static final String TIME = "^([01][0-9]|2[0-3]):[0-5][0-9]$";
    /** Letters, spaces, dots, apostrophes and hyphens (e.g. "Vallabh Vidyanagar"). */
    public static final String CITY = "^[\\p{L}][\\p{L} .'-]*$";
    /** Expertise tags are stored comma-separated, so they can't contain commas or semicolons. */
    public static final String EXPERTISE = "^[^,;]+$";
    public static final String SEARCH_SORT = "^(rating|price_asc|price_desc|name|newest)$";

    public static final int MAX_SERVICES = 30;
    public static final int MAX_EXPERTISE = 10;

    private RepairerValidation() {
    }
}
