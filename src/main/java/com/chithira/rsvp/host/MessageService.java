package com.chithira.rsvp.host;

import com.chithira.rsvp.event.Event;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and saves the host's message templates, falling back to defaults built from the event. */
@Service
public class MessageService {

    private final EventMessageRepository messages;

    public MessageService(EventMessageRepository messages) {
        this.messages = messages;
    }

    /** What the composer shows: stored templates, or the defaults. */
    public record Composer(String templateEn, String templateMl, MessageLanguage language) {
        public String active() {
            return language == MessageLanguage.ML ? templateMl : templateEn;
        }
    }

    public static class InvalidMessageException extends RuntimeException {
        private final MessageLanguage language;

        InvalidMessageException(MessageLanguage language, String message) {
            super(message);
            this.language = language;
        }

        public MessageLanguage getLanguage() {
            return language;
        }
    }

    @Transactional(readOnly = true)
    public Composer composer(Event event) {
        Optional<EventMessage> stored = messages.findById(event.getId());
        return new Composer(
                stored.map(EventMessage::getTemplateEn).orElse(WhatsAppMessages.defaultTemplate(event, MessageLanguage.EN)),
                stored.map(EventMessage::getTemplateMl).orElse(WhatsAppMessages.defaultTemplate(event, MessageLanguage.ML)),
                stored.map(EventMessage::getLanguage).orElse(MessageLanguage.EN));
    }

    /**
     * Saves both templates and the chosen language. Only the language that will be sent must be valid;
     * the other may be left mid-edit as long as it fits. A template equal to the default is stored as
     * null so it keeps following the event's messages.
     */
    @Transactional
    public Composer save(Event event, String templateEn, String templateMl, MessageLanguage language) {
        String en = normalise(templateEn);
        String ml = normalise(templateMl);
        String active = language == MessageLanguage.ML ? ml : en;
        WhatsAppMessages.problem(active).ifPresent(p -> {
            throw new InvalidMessageException(language, p);
        });
        for (var other : new String[] {en, ml}) {
            if (other != null && other.length() > WhatsAppMessages.MAX_LENGTH) {
                throw new InvalidMessageException(language, "A message is longer than " + WhatsAppMessages.MAX_LENGTH + " characters.");
            }
        }
        var row = messages.findById(event.getId()).orElseGet(() -> new EventMessage(event.getId()));
        row.update(
                orNullIfDefault(en, WhatsAppMessages.defaultTemplate(event, MessageLanguage.EN)),
                orNullIfDefault(ml, WhatsAppMessages.defaultTemplate(event, MessageLanguage.ML)),
                language);
        messages.save(row);
        return composer(event);
    }

    @Transactional
    public void reset(Event event) {
        messages.findById(event.getId()).ifPresent(messages::delete);
    }

    /** Browsers send textarea line breaks as CRLF; store LF like the defaults. */
    private static String normalise(String s) {
        return s == null ? null : s.replace("\r\n", "\n").strip();
    }

    private static String orNullIfDefault(String value, String defaultValue) {
        return value == null || value.equals(defaultValue) ? null : value;
    }
}
