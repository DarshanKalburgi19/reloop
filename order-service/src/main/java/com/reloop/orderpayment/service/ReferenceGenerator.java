package com.reloop.orderpayment.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Human-readable unique references: {@code PREFIX-yyyyMMdd-XXXXXXXXXX} with 10 random Crockford base-32
 * characters (~50 bits). Unique constraints on the columns are the final guard.
 * Prefixes: {@code PAY} payment transaction, {@code COM} commission record, {@code PO} provider payout.
 */
@Component
public class ReferenceGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ReferenceGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append('-')
                .append(LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
