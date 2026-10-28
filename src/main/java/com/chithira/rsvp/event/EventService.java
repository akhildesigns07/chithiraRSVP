package com.chithira.rsvp.event;

import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {

    private final EventRepository events;
    private final EventPhotoRepository photos;
    private final LetterPageRepository letters;

    public EventService(EventRepository events, EventPhotoRepository photos, LetterPageRepository letters) {
        this.events = events;
        this.photos = photos;
        this.letters = letters;
    }

    /** Files that come with the event form. An empty letter list leaves the current letter alone. */
    public record Uploads(MultipartFile photo, List<MultipartFile> letter, boolean removeLetter) {
        public static Uploads none() {
            return new Uploads(null, List.of(), false);
        }
    }

    @Transactional(readOnly = true)
    public List<Event> listAll() {
        return events.findAllByOrderByStartsAtDesc();
    }

    @Transactional(readOnly = true)
    public Event get(long id) {
        return events.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Event> findByHostToken(String hostToken) {
        return events.findByHostToken(hostToken);
    }

    /** Validates uploads before touching the database so a bad file never saves a half-edited event. */
    @Transactional
    public Event create(EventForm form, Uploads uploads) {
        var photo = UploadValidator.photo(uploads.photo());
        var letter = UploadValidator.letter(uploads.letter());
        var event = new Event();
        form.applyTo(event);
        events.save(event);
        photo.ifPresent(p -> photos.save(new EventPhoto(event.getId(), p.contentType(), p.data())));
        saveLetter(event.getId(), letter);
        return event;
    }

    @Transactional
    public Event update(long id, EventForm form, Uploads uploads) {
        var photo = UploadValidator.photo(uploads.photo());
        var letter = UploadValidator.letter(uploads.letter());
        var event = get(id);
        form.applyTo(event);
        photo.ifPresent(p -> photos.findById(id).ifPresentOrElse(
                existing -> existing.replace(p.contentType(), p.data()),
                () -> photos.save(new EventPhoto(id, p.contentType(), p.data()))));
        if (!letter.isEmpty() || uploads.removeLetter()) {
            letters.deleteByEventId(id);
            saveLetter(id, letter);
        }
        return event;
    }

    private void saveLetter(long eventId, List<UploadValidator.ValidFile> files) {
        for (int i = 0; i < files.size(); i++) {
            var f = files.get(i);
            letters.save(new LetterPage(eventId, i + 1, f.contentType(), f.filename(), f.data()));
        }
    }

    @Transactional(readOnly = true)
    public Optional<EventPhoto> photo(long eventId) {
        return photos.findById(eventId);
    }

    @Transactional(readOnly = true)
    public boolean hasPhoto(long eventId) {
        return photos.existsById(eventId);
    }

    @Transactional(readOnly = true)
    public List<LetterPageInfo> letterPages(long eventId) {
        return letters.findInfoByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public Optional<LetterPage> letterPage(long eventId, int pageNo) {
        return letters.findByEventIdAndPageNo(eventId, pageNo);
    }
}
