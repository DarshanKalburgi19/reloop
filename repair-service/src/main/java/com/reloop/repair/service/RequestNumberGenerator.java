package com.reloop.repair.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Human-friendly, non-guessable repair request numbers: {@code RR-20261125-7K3M9QXD}
 * (date + 8 Crockford base-32 characters, no I/L/O/U). Uniqueness is enforced by
 * {@code uk_requests_number}; the service retries on the (astronomically unlikely) clash.
 */
@Component
public class RequestNumberGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public RequestNumberGenerator() {
        this(Clock.systemDefaultZone());
    }

    RequestNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder sb = new StringBuilder("RR-").append(LocalDate.now(clock).format(DAY)).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
