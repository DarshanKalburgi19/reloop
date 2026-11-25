package com.reloop.repair.service;

import com.reloop.repair.enums.SparePartCategory;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** Generates SKUs like "BAT-7F3K2Q9A" (3-letter category prefix + 8 random characters) for parts added without one. */
@Component
public class SkuGenerator {

    /** No 0/O or 1/I/L, so SKUs read back unambiguously from a label. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int SUFFIX_LENGTH = 8;

    private final RandomGenerator random;

    public SkuGenerator() {
        this(new SecureRandom());
    }

    SkuGenerator(RandomGenerator random) {
        this.random = random;
    }

    public String next(SparePartCategory category) {
        SparePartCategory c = category == null ? SparePartCategory.OTHER : category;
        StringBuilder sb = new StringBuilder(c.name().substring(0, 3)).append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
