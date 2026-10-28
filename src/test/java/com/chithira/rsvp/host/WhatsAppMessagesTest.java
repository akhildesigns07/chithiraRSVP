package com.chithira.rsvp.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.chithira.rsvp.config.AppProperties;
import com.chithira.rsvp.config.Links;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import org.junit.jupiter.api.Test;

class WhatsAppMessagesTest {

    private final WhatsAppMessages messages =
            new WhatsAppMessages(new Links(new AppProperties("https://inv.example/", null, null, null)));

    private static Event event() {
        var event = new Event();
        event.setMessageEn("We invite you.");
        event.setMessageMl("സ്വാഗതം");
        return event;
    }

    @Test
    void defaultsGreetByNameAndEndWithTheLink() {
        assertThat(WhatsAppMessages.defaultTemplate(event(), MessageLanguage.EN)).isEqualTo("Dear {name},\n\nWe invite you.\n\n{link}");
        assertThat(WhatsAppMessages.defaultTemplate(event(), MessageLanguage.ML)).isEqualTo("പ്രിയപ്പെട്ട {name},\n\nസ്വാഗതം\n\n{link}");
    }

    @Test
    void rendersEveryPlaceholderOccurrence() {
        var guest = new Guest(event(), "Anil", "919876543210");
        assertThat(messages.render("Hi {name}! {name}, open {link}", guest))
                .isEqualTo("Hi Anil! Anil, open https://inv.example/i/" + guest.getToken());
    }

    @Test
    void templatesMustContainTheLinkAndFit() {
        assertThat(WhatsAppMessages.problem("Hi {name} {link}")).isEmpty();
        assertThat(WhatsAppMessages.problem("Hi {name}")).get().asString().contains("{link}");
        assertThat(WhatsAppMessages.problem("  ")).isPresent();
        assertThat(WhatsAppMessages.problem(null)).isPresent();
        assertThat(WhatsAppMessages.problem("{link}" + "x".repeat(1000))).get().asString().contains("too long");
    }

    @Test
    void encodesEverythingThatIsNotUnreserved() {
        String url = WhatsAppMessages.waMeUrl("919876543210", "Dear Anil,\n\nA+B & C? സ https://x/i/t");
        assertThat(url).isEqualTo("https://wa.me/919876543210?text=Dear%20Anil%2C%0A%0AA%2BB%20%26%20C%3F%20%E0%B4%B8%20https%3A%2F%2Fx%2Fi%2Ft");
    }
}
