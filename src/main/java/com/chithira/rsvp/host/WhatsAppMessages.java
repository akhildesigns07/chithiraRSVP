package com.chithira.rsvp.host;

import com.chithira.rsvp.config.Links;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class WhatsAppMessages {

    private final Links links;

    public WhatsAppMessages(Links links) {
        this.links = links;
    }

    public String message(Guest guest, Event event) {
        return "Dear " + guest.getName() + ",\n\n"
                + event.getMessageEn() + "\n\n"
                + event.getMessageMl() + "\n\n"
                + links.invite(guest);
    }

    /** Click-to-chat URL. Everything outside RFC 3986 unreserved characters is percent-encoded, including '+'. */
    public String waMeUrl(Guest guest, Event event) {
        return "https://wa.me/" + guest.getPhone() + "?text="
                + UriUtils.encode(message(guest, event), StandardCharsets.UTF_8);
    }
}
