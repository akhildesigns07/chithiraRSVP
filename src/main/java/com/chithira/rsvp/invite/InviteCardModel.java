package com.chithira.rsvp.invite;

import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestService;
import org.springframework.ui.Model;

/**
 * Builds the model for {@code invite/card}. The card only ever uses the URLs given here,
 * so the same templates serve real guests and the staff preview.
 */
public final class InviteCardModel {

    private InviteCardModel() {
    }

    public static void forGuest(Model model, Guest guest, boolean hasPhoto, boolean hasLetter) {
        String base = "/i/" + guest.getToken();
        common(model, guest, guest.getEvent(), guest.getEvent().getTemplate());
        model.addAttribute("photoUrl", hasPhoto ? base + "/photo" : null);
        model.addAttribute("letterUrl", hasLetter ? base + "/letter" : null);
        model.addAttribute("rsvpUrl", base + "/rsvp");
    }

    /** Staff preview: a sample guest, staff-only asset URLs, and replies switched off. */
    public static void forPreview(Model model, Event event, CardTemplate template, boolean hasPhoto, boolean hasLetter) {
        String base = "/portal/events/" + event.getId();
        common(model, new Guest(event, "Guest Name", ""), event, template);
        model.addAttribute("photoUrl", hasPhoto ? base + "/photo" : null);
        model.addAttribute("letterUrl", hasLetter ? base + "/letter/1" : null);
        model.addAttribute("rsvpUrl", null);
        model.addAttribute("preview", true);
    }

    /** The htmx RSVP response: just the section, which carries no entrance animation of its own. */
    static void forRsvpFragment(Model model, Guest guest) {
        model.addAttribute("guest", guest);
        model.addAttribute("rsvpUrl", "/i/" + guest.getToken() + "/rsvp");
        model.addAttribute("maxHeadcount", GuestService.MAX_HEADCOUNT);
    }

    private static void common(Model model, Guest guest, Event event, CardTemplate template) {
        model.addAttribute("guest", guest);
        model.addAttribute("event", event);
        model.addAttribute("template", template);
        model.addAttribute("maxHeadcount", GuestService.MAX_HEADCOUNT);
    }
}
