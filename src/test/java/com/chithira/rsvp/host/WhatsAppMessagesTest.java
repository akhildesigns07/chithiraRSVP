package com.chithira.rsvp.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.chithira.rsvp.config.AppProperties;
import com.chithira.rsvp.config.Links;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import org.junit.jupiter.api.Test;

class WhatsAppMessagesTest {

    private final WhatsAppMessages messages =
            new WhatsAppMessages(new Links(new AppProperties("https://inv.example/", null, null)));

    @Test
    void encodesEverythingThatIsNotUnreserved() {
        var event = new Event();
        event.setMessageEn("A+B & C?");
        event.setMessageMl("സ്വാഗതം");
        var guest = new Guest(event, "Anil", "919876543210");

        String url = messages.waMeUrl(guest, event);

        assertThat(url).startsWith("https://wa.me/919876543210?text=Dear%20Anil%2C%0A%0AA%2BB%20%26%20C%3F%0A%0A%E0%B4");
        assertThat(url).endsWith("%0A%0Ahttps%3A%2F%2Finv.example%2Fi%2F" + guest.getToken());
    }
}
