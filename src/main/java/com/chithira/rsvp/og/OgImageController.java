package com.chithira.rsvp.og;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public share image for link previews (WhatsApp fetches it without any session). */
@RestController
public class OgImageController {

    private final OgImageService images;

    public OgImageController(OgImageService images) {
        this.images = images;
    }

    @GetMapping("/og/{slug}.jpg")
    ResponseEntity<byte[]> image(@PathVariable String slug) {
        return images.imageForSlug(slug)
                .map(jpeg -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        // The page links it with ?v=<version>, so a changed event gets a new URL.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(jpeg))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
