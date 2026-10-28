package com.chithira.rsvp.logging;

import java.util.regex.Pattern;

/**
 * Removes personal data and credentials from log text: phone numbers, URL tokens (22-char base64url)
 * and token hashes (64-char hex). Applied to every log line in production as a safety net, e.g. for
 * database errors that echo row values ("Key (event_id, phone)=(1, 919876543210) already exists").
 */
public final class LogRedactor {

    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{22}(?![A-Za-z0-9_-])");
    private static final Pattern HASH = Pattern.compile("(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])");
    /** 9-15 digits, optionally with a leading + and single spaces/hyphens between digits. */
    private static final Pattern PHONE = Pattern.compile("(?<![\\d+])\\+?\\d(?:[ -]?\\d){8,14}(?!\\d)");

    private LogRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = HASH.matcher(text).replaceAll("[hash]");
        s = TOKEN.matcher(s).replaceAll("[token]");
        return PHONE.matcher(s).replaceAll("[phone]");
    }
}
