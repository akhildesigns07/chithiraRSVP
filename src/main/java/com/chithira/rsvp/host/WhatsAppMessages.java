package com.chithira.rsvp.host;

import com.chithira.rsvp.config.Links;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/** Builds the personal WhatsApp message from a template with {name} and {link} placeholders. */
@Component
public class WhatsAppMessages {

    public static final String NAME = "{name}";
    public static final String LINK = "{link}";
    /** Malayalam is ~9 bytes per character once percent-encoded; this keeps wa.me URLs a safe length. */
    public static final int MAX_LENGTH = 1000;

    private final Links links;

    public WhatsAppMessages(Links links) {
        this.links = links;
    }

    public static String defaultTemplate(Event event, MessageLanguage language) {
        return switch (language) {
            case EN -> "Dear " + NAME + ",\n\n" + event.getMessageEn() + "\n\n" + LINK;
            case ML -> "പ്രിയപ്പെട്ട " + NAME + ",\n\n" + event.getMessageMl() + "\n\n" + LINK;
        };
    }

    /** Why a template can't be used, if it can't. */
    public static Optional<String> problem(String template) {
        if (template == null || template.isBlank()) {
            return Optional.of("The message is empty.");
        }
        if (!template.contains(LINK)) {
            return Optional.of("The message must include " + LINK + " so the guest gets their invitation link.");
        }
        if (template.length() > MAX_LENGTH) {
            return Optional.of("The message is too long (" + template.length() + " of " + MAX_LENGTH + " characters).");
        }
        return Optional.empty();
    }

    public String render(String template, Guest guest) {
        return template.replace(NAME, guest.getName()).replace(LINK, links.invite(guest));
    }

    /** Click-to-chat URL. Everything outside RFC 3986 unreserved characters is percent-encoded, including '+'. */
    public static String waMeUrl(String phone, String text) {
        return "https://wa.me/" + phone + "?text=" + UriUtils.encode(text, StandardCharsets.UTF_8);
    }
}
