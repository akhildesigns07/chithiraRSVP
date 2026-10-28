package com.chithira.rsvp.og;

import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventPhoto;
import com.chithira.rsvp.event.EventPhotoRepository;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.util.Slugs;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serves each event's share image from the database cache, re-rendering it when the event has
 * changed since (the cached version is the event's {@code updated_at}).
 */
@Service
public class OgImageService {

    private final EventRepository events;
    private final EventPhotoRepository photos;
    private final OgImageRepository images;
    private final OgImageRenderer renderer;
    private final TransactionTemplate tx;
    /** One render at a time per event, so a burst of link previews renders once. */
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    public OgImageService(EventRepository events, EventPhotoRepository photos, OgImageRepository images,
                          OgImageRenderer renderer, TransactionTemplate tx) {
        this.events = events;
        this.photos = photos;
        this.images = images;
        this.renderer = renderer;
        this.tx = tx;
    }

    public static long version(Event event) {
        return event.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public Optional<byte[]> imageForSlug(String slug) {
        if (!Slugs.looksValid(slug)) {
            return Optional.empty();
        }
        Optional<Event> found = tx.execute(s -> events.findByOgSlug(slug));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Event event = found.get();
        long version = version(event);
        Optional<byte[]> cached = cached(event.getId(), version);
        if (cached.isPresent()) {
            return cached;
        }
        synchronized (locks.computeIfAbsent(event.getId(), id -> new Object())) {
            cached = cached(event.getId(), version);
            if (cached.isPresent()) {
                return cached;
            }
            byte[] jpeg = renderer.render(event, tx.execute(s -> photos.findById(event.getId()).map(EventPhoto::getData)));
            tx.executeWithoutResult(s -> images.findById(event.getId()).ifPresentOrElse(
                    existing -> existing.replace(version, jpeg),
                    () -> images.save(new OgImage(event.getId(), version, jpeg))));
            return Optional.of(jpeg);
        }
    }

    private Optional<byte[]> cached(long eventId, long version) {
        return tx.execute(s -> images.findById(eventId).filter(i -> i.getVersion() == version).map(OgImage::getJpeg));
    }
}
