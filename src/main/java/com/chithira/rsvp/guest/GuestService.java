package com.chithira.rsvp.guest;

import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.util.Tokens;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestService {

    public static final int MAX_HEADCOUNT = 20;

    private final GuestRepository guests;

    public GuestService(GuestRepository guests) {
        this.guests = guests;
    }

    @Transactional(readOnly = true)
    public List<Guest> forEvent(long eventId) {
        return guests.findByEventIdOrderById(eventId);
    }

    /**
     * Adds every guest from the pasted text, or none of them if any line is invalid
     * (including numbers already on this event's list), so staff can fix and re-paste.
     */
    @Transactional
    public GuestListParser.Result addFromPaste(Event event, String text) {
        var parsed = GuestListParser.parse(text);
        var errors = new ArrayList<>(parsed.errors());
        var valid = new ArrayList<GuestListParser.ParsedGuest>();
        for (var g : parsed.guests()) {
            if (guests.existsByEventIdAndPhone(event.getId(), g.phone())) {
                errors.add(new GuestListParser.LineError(g.lineNumber(),
                        g.name() + ", " + PhoneNormalizer.format(g.phone()), "Already on the guest list"));
            } else {
                valid.add(g);
            }
        }
        if (!errors.isEmpty()) {
            errors.sort(Comparator.comparingInt(GuestListParser.LineError::lineNumber));
            return new GuestListParser.Result(List.of(), errors);
        }
        valid.forEach(g -> guests.save(new Guest(event, g.name(), g.phone())));
        return new GuestListParser.Result(valid, List.of());
    }

    /** Looks up by token hash, then confirms the token itself in constant time. */
    @Transactional(readOnly = true)
    public Optional<Guest> findByToken(String token) {
        if (!Tokens.looksValid(token)) {
            return Optional.empty();
        }
        return guests.findByTokenHash(Tokens.hash(token))
                .filter(g -> Tokens.matches(g.getToken(), token));
    }

    /** Records the guest's reply. The guest is re-read within its own event, never by id alone. */
    @Transactional
    public Guest respond(Guest guest, boolean attending, Integer headcount) {
        var managed = guests.findByIdAndEventId(guest.getId(), guest.getEvent().getId()).orElseThrow();
        if (attending) {
            if (headcount == null || headcount < 1 || headcount > MAX_HEADCOUNT) {
                throw new IllegalArgumentException("Headcount must be between 1 and " + MAX_HEADCOUNT);
            }
            managed.attend(headcount);
        } else {
            managed.decline();
        }
        return managed;
    }

    @Transactional
    public Optional<Guest> markSent(long eventId, long guestId) {
        var guest = guests.findByIdAndEventId(guestId, eventId);
        guest.ifPresent(Guest::markSent);
        return guest;
    }

    /** The first guest, in list order, who has not been sent the invite yet. */
    @Transactional(readOnly = true)
    public Optional<Guest> nextUnsent(long eventId) {
        return forEvent(eventId).stream().filter(g -> !g.isSent()).findFirst();
    }
}
