package com.chithira.rsvp;

import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.event.EventType;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class TestData {

    /** Smallest valid PNG signature followed by filler; enough for signature detection. */
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    public static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes();

    private final EventRepository events;
    private final GuestRepository guests;

    public TestData(EventRepository events, GuestRepository guests) {
        this.events = events;
        this.guests = guests;
    }

    public Event event() {
        var e = new Event();
        e.setCoupleNames("Anjali & Rahul");
        e.setEventType(EventType.WEDDING);
        e.setStartsAt(LocalDateTime.of(2027, 2, 14, 10, 30));
        e.setVenue("St. Mary's Church, Kottayam");
        e.setMapsUrl("https://maps.app.goo.gl/abc123");
        e.setTemplate(CardTemplate.KERALA_TRADITIONAL);
        e.setMessageEn("We joyfully invite you to celebrate our wedding.");
        e.setMessageMl("ഞങ്ങളുടെ വിവാഹത്തിലേക്ക് നിങ്ങളെ സ്നേഹപൂർവ്വം ക്ഷണിക്കുന്നു.");
        return events.save(e);
    }

    public Guest guest(Event event, String name, String phone) {
        return guests.save(new Guest(event, name, phone));
    }
}
