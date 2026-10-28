package com.chithira.rsvp.invite;

import com.chithira.rsvp.config.Links;
import com.chithira.rsvp.event.EventService;
import com.chithira.rsvp.event.LetterPageInfo;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestService;
import com.chithira.rsvp.util.FileResponses;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Public, per-guest invitation card. Must never expose phone numbers. */
@Controller
@RequestMapping("/i/{token}")
public class InviteController {

    private final GuestService guests;
    private final EventService events;
    private final Links links;

    public InviteController(GuestService guests, EventService events, Links links) {
        this.guests = guests;
        this.events = events;
        this.links = links;
    }

    @GetMapping
    String card(@PathVariable String token, Model model) {
        Guest guest = guest(token);
        long eventId = guest.getEvent().getId();
        InviteCardModel.forGuest(model, guest, events.hasPhoto(eventId), !events.letterPages(eventId).isEmpty(),
                links.ogImage(guest.getEvent()));
        return "invite/card";
    }

    @GetMapping("/photo")
    ResponseEntity<byte[]> photo(@PathVariable String token) {
        return FileResponses.photo(events.photo(guest(token).getEvent().getId()), FileResponses.PERSONAL);
    }

    /** The full printed invitation letter: a PDF viewer or the page images, one after another. */
    @GetMapping("/letter")
    String letter(@PathVariable String token, Model model) {
        Guest guest = guest(token);
        List<LetterPageInfo> pages = events.letterPages(guest.getEvent().getId());
        if (pages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("guest", guest);
        model.addAttribute("event", guest.getEvent());
        model.addAttribute("pages", pages);
        model.addAttribute("isPdf", pages.getFirst().isPdf());
        return "invite/letter";
    }

    @GetMapping("/letter/{page}")
    ResponseEntity<byte[]> letterPage(@PathVariable String token, @PathVariable int page,
                                      @RequestParam(defaultValue = "false") boolean download) {
        return FileResponses.letterPage(events.letterPage(guest(token).getEvent().getId(), page), download, FileResponses.PERSONAL);
    }

    @PostMapping("/rsvp")
    String rsvp(@PathVariable String token,
                @RequestParam(required = false) String attending,
                @RequestParam(required = false) Integer headcount,
                @RequestHeader(name = "HX-Request", required = false) String htmx,
                Model model) {
        Guest guest = guest(token);
        if (!"yes".equals(attending) && !"no".equals(attending)) {
            model.addAttribute("rsvpError", true);
        } else {
            try {
                guest = guests.respond(guest, "yes".equals(attending), headcount);
            } catch (IllegalArgumentException e) {
                model.addAttribute("rsvpError", true);
            }
        }
        if (htmx == null) {
            return "redirect:/i/" + token + "#rsvp";
        }
        InviteCardModel.forRsvpFragment(model, guest);
        model.addAttribute("justResponded", !model.containsAttribute("rsvpError"));
        return "invite/parts :: rsvp";
    }

    private Guest guest(String token) {
        return guests.findByToken(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
