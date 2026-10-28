package com.chithira.rsvp.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Unguessable URL tokens: 128 random bits, base64url without padding (22 chars).
 *
 * <p>Tokens are looked up by their SHA-256 hash, then compared in constant time. The database
 * comparison therefore only ever sees a hash the caller cannot steer, so response timing reveals
 * nothing about how much of a real token was guessed.
 */
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

    /** Lowercase hex SHA-256 of the token (64 chars); the indexed lookup key. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time equality, independent of where the first differing character is. */
    public static boolean matches(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
