package com.reloop.orderpayment.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Paise ⇄ rupee formatting for messages and receipts (amounts are always stored as integer paise). */
public final class Money {

    public static final String CURRENCY = "INR";

    private Money() {
    }

    /** 102000 → "₹1,020.00" (Indian digit grouping). */
    public static String format(long paise) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        String sign = paise < 0 ? "-" : "";
        return sign + "₹" + nf.format(BigDecimal.valueOf(Math.abs(paise), 2));
    }

    /** 0.020000 → "2%", 0.025 → "2.5%". */
    public static String percent(BigDecimal rate) {
        return rate.movePointRight(2).stripTrailingZeros().toPlainString() + "%";
    }
}
