package com.chithira.rsvp.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Public slugs for share images, e.g. {@code anjali-rahul-k3f9x2m7q1zc}. The readable part is cosmetic;
 * the 12-character random suffix (~62 bits) keeps slugs unguessable, so event photos can't be enumerated.
 */
public final class Slugs {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private static final int MAX_READABLE = 40;

    private Slugs() {
    }

    public static String forEvent(String coupleNames) {
        String ascii = Normalizer.normalize(coupleNames == null ? "" : coupleNames, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")              // é -> e (drop the separated accents)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (ascii.length() > MAX_READABLE) {
            // Cut at a word boundary where possible.
            int cut = ascii.charAt(MAX_READABLE) == '-' ? MAX_READABLE : ascii.lastIndexOf('-', MAX_READABLE);
            ascii = ascii.substring(0, cut > 0 ? cut : MAX_READABLE);
        }
        StringBuilder sb = new StringBuilder(ascii.isEmpty() ? "event" : ascii).append('-');
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static boolean looksValid(String slug) {
        return slug != null && slug.matches("[a-z0-9-]{14,80}");
    }
}
