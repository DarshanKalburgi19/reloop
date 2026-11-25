package com.reloop.product.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Rupee/paise conversion. Money is always stored as integer paise, never as floating point. */
public final class Money {

    public static final String CURRENCY = "INR";

    private Money() {
    }

    /** ₹1,300.50 -> 130050. The request is already validated to at most 2 decimals. */
    public static long toPaise(BigDecimal rupees) {
        return rupees.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** 130050 -> 1300.50 (scale 2). */
    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    /** 130050 -> "₹1,300.50" (used in history details). */
    public static String format(long paise) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return "\u20B9" + format.format(toRupees(paise));
    }
}
