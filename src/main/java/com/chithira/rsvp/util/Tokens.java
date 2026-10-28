package com.chithira.rsvp.util;

import java.security.SecureRandom;
import java.util.Base64;

/** Unguessable URL tokens: 128 random bits, base64url without padding (22 chars). */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Cheap shape check so obviously bad tokens never reach the database. */
    public static boolean looksValid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{22}");
    }
}
