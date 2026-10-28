package com.chithira.rsvp.util;

import com.chithira.rsvp.event.EventPhoto;
import com.chithira.rsvp.event.LetterPage;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class FileResponses {

    private FileResponses() {
    }

    /** Guest-facing files live under /i/{token}: personal, so never stored by browsers or Cloudflare. */
    public static final CacheControl PERSONAL = CacheControl.noStore();
    /** Staff-only copies: the staff browser may keep them briefly. */
    public static final CacheControl STAFF = CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate();

    public static ResponseEntity<byte[]> photo(Optional<EventPhoto> photo, CacheControl cache) {
        return photo
                .map(p -> ok(p.getContentType(), cache).body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static ResponseEntity<byte[]> letterPage(Optional<LetterPage> page, boolean download, CacheControl cache) {
        return page
                .map(p -> ok(p.getContentType(), cache)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                                        .filename(p.getFilename()).build().toString())
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity.BodyBuilder ok(String contentType, CacheControl cache) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(cache)
                .header("X-Content-Type-Options", "nosniff");
    }
}
