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

    public static ResponseEntity<byte[]> photo(Optional<EventPhoto> photo) {
        return photo
                .map(p -> ok(p.getContentType()).body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static ResponseEntity<byte[]> letterPage(Optional<LetterPage> page, boolean download) {
        return page
                .map(p -> ok(p.getContentType())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                                        .filename(p.getFilename()).build().toString())
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity.BodyBuilder ok(String contentType) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff");
    }
}
