package com.reloop.repair.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Simulated tracking IDs: {@code TRK-SIM-20261125-7K3M9QXD} (date + 8 Crockford base-32 characters). The
 * {@code SIM} part makes it obvious the number belongs to no courier. Unique via {@code uk_deliveries_tracking_number}.
 */
@Component
public class TrackingNumberGenerator {

    public static final String PREFIX = "TRK-SIM-";
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TrackingNumberGenerator() {
        this(Clock.systemDefaultZone());
    }

    TrackingNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        StringBuilder sb = new StringBuilder(PREFIX).append(LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE))
                .append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
