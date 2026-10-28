package com.chithira.rsvp.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploaded photos and letters. File types are identified by their signature,
 * never by the name or content type the browser sends.
 */
public final class UploadValidator {

    public static final int MAX_PHOTO_BYTES = 1024 * 1024;
    public static final int MAX_LETTER_BYTES = 10 * 1024 * 1024;
    public static final int MAX_LETTER_PAGES = 20;
    static final String PDF = "application/pdf";

    public record ValidFile(String contentType, String filename, byte[] data) {
    }

    private UploadValidator() {
    }

    /** Returns the detected image type (JPEG, PNG or WebP), or empty. */
    public static Optional<String> detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of("image/png");
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    static boolean isPdf(byte[] b) {
        return b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    static Optional<ValidFile> photo(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return Optional.empty();
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new InvalidUploadException("Photo must be 1 MB or smaller.");
        }
        byte[] data = bytes(photo);
        String type = detectImageType(data)
                .orElseThrow(() -> new InvalidUploadException("Photo must be a JPEG, PNG or WebP image."));
        return Optional.of(new ValidFile(type, safeName(photo.getOriginalFilename(), "photo"), data));
    }

    /** A letter is either a single PDF or up to 20 page images, 10 MB in total. Empty list = no change. */
    static List<ValidFile> letter(List<MultipartFile> files) {
        List<MultipartFile> present = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (present.isEmpty()) {
            return List.of();
        }
        long total = present.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > MAX_LETTER_BYTES) {
            throw new InvalidUploadException("The letter must be 10 MB or smaller in total.");
        }
        if (present.size() > MAX_LETTER_PAGES) {
            throw new InvalidUploadException("The letter can have at most " + MAX_LETTER_PAGES + " pages.");
        }
        List<ValidFile> result = new ArrayList<>();
        for (MultipartFile f : present) {
            byte[] data = bytes(f);
            String type;
            if (isPdf(data)) {
                type = PDF;
            } else {
                type = detectImageType(data).orElseThrow(() -> new InvalidUploadException(
                        "Letter files must be a PDF or JPEG, PNG or WebP images."));
            }
            result.add(new ValidFile(type, safeName(f.getOriginalFilename(), "letter"), data));
        }
        if (result.size() > 1 && result.stream().anyMatch(v -> v.contentType().equals(PDF))) {
            throw new InvalidUploadException("Upload either one PDF or page images, not both.");
        }
        return result;
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Keeps only the base name and characters safe for a Content-Disposition header. */
    static String safeName(String original, String fallback) {
        if (original == null) {
            return fallback;
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").strip();
        if (base.isEmpty() || base.chars().allMatch(c -> c == '.' || c == '_')) {
            return fallback;
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }
}
