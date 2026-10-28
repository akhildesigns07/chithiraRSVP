package com.chithira.rsvp.guest;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalises Indian and UAE mobile numbers to digits-only international form
 * (e.g. {@code 919876543210}, {@code 971501234567}), which is what wa.me expects.
 *
 * <p>Accepted inputs include {@code +91 98765 43210}, {@code 09876543210}, {@code 9876543210},
 * {@code +971 50 123 4567}, {@code 00971501234567}, {@code 050-123-4567} and {@code 501234567}.
 * Anything else (landlines, other countries) is rejected.
 */
public final class PhoneNormalizer {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");
    private static final Pattern INDIA_MOBILE = Pattern.compile("[6-9]\\d{9}");
    private static final Pattern UAE_MOBILE = Pattern.compile("5\\d{8}");

    private PhoneNormalizer() {
    }

    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = SEPARATORS.matcher(raw.strip()).replaceAll("");
        boolean international = false;
        if (s.startsWith("+")) {
            s = s.substring(1);
            international = true;
        } else if (s.startsWith("00")) {
            s = s.substring(2);
            international = true;
        }
        if (!s.matches("\\d+")) {
            return Optional.empty();
        }

        if (s.startsWith("91") && INDIA_MOBILE.matcher(s.substring(2)).matches()) {
            return Optional.of(s);
        }
        if (s.startsWith("971") && UAE_MOBILE.matcher(s.substring(3)).matches()) {
            return Optional.of(s);
        }
        if (international) {
            return Optional.empty();
        }

        // National formats: an optional trunk "0" followed by the subscriber number.
        String national = s.startsWith("0") ? s.substring(1) : s;
        if (INDIA_MOBILE.matcher(national).matches()) {
            return Optional.of("91" + national);
        }
        if (UAE_MOBILE.matcher(national).matches()) {
            return Optional.of("971" + national);
        }
        return Optional.empty();
    }

    /** Human-readable form for staff screens, e.g. {@code +91 98765 43210}. */
    public static String format(String normalized) {
        if (normalized.startsWith("91") && normalized.length() == 12) {
            return "+91 " + normalized.substring(2, 7) + " " + normalized.substring(7);
        }
        if (normalized.startsWith("971") && normalized.length() == 12) {
            return "+971 " + normalized.substring(3, 5) + " " + normalized.substring(5, 8) + " " + normalized.substring(8);
        }
        return "+" + normalized;
    }
}
