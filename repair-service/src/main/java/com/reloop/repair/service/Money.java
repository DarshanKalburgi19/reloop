package com.reloop.repair.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Rupee/paise conversion. Prices are stored as integer paise, never floating point. */
public final class Money {

    private Money() {
    }

    /** ₹1,499.50 -> 149950. Requests are validated to at most 2 decimals. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    /** Search bounds: minPrice is rounded up and maxPrice down to whole paise. */
    public static long ceilPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.CEILING).longValueExact();
    }

    public static long floorPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /** 149900 -> "₹1,499"; 19950 -> "₹199.50". */
    public static String format(long paise) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        String pattern = paise % 100 == 0 ? "#,##0" : "#,##0.00";
        return "\u20B9" + new DecimalFormat(pattern, symbols).format(toRupees(paise));
    }
}
