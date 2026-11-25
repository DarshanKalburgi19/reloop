package com.reloop.product.service;

import com.reloop.product.repository.ProductPassportRepository;
import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates unique, hard-to-guess passport codes such as {@code RLP-7K3Q-X9M2-B4TD}.
 *
 * <ul>
 *   <li>12 random characters from Crockford base32 (no I, L, O or U, so codes are easy to read
 *       aloud and to type from a printed label or QR code): 60 bits of randomness.</li>
 *   <li>Drawn from {@link SecureRandom}, so codes can't be predicted or enumerated.</li>
 *   <li>Uniqueness is checked before use, and the {@code uk_passports_code} unique constraint
 *       is the final guarantee.</li>
 * </ul>
 */
@Component
public class PassportCodeGenerator {

    public static final String PREFIX = "RLP";
    public static final Pattern FORMAT = Pattern.compile("^RLP-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$");

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int MAX_ATTEMPTS = 10;

    private final ProductPassportRepository passports;
    private final SecureRandom random = new SecureRandom();

    public PassportCodeGenerator(ProductPassportRepository passports) {
        this.passports = passports;
    }

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!passports.existsByPassportCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique passport code");
    }

    String randomCode() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int group = 0; group < 3; group++) {
            sb.append('-');
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return sb.toString();
    }
}
