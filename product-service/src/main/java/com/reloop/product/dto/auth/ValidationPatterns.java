package com.reloop.product.dto.auth;

/** Shared regular expressions for request validation (kept in one place for docs and tests). */
public final class ValidationPatterns {

    /** Optional leading +, then 10-15 digits. Examples: 9876543210, +919876543210. */
    public static final String PHONE = "^\\+?[0-9]{10,15}$";

    /** Indian PIN code: 6 digits, first digit 1-9. */
    public static final String PINCODE = "^[1-9][0-9]{5}$";

    /** At least one letter and at least one digit. Length rules are separate (@Size). */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*[0-9]).+$";

    public static final String PASSWORD_MESSAGE = "must contain at least one letter and one digit";

    private ValidationPatterns() {
    }
}
