package com.chithira.rsvp.host;

import com.chithira.rsvp.config.AppProperties;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventService;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestService;
import com.chithira.rsvp.host.MessageService.Composer;
import com.chithira.rsvp.host.MessageService.InvalidMessageException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Host dashboard, reachable with the event's host token. Phone numbers never appear in its pages or export. */
@Controller
@RequestMapping("/h/{hostToken}")
public class HostController {

    private final EventService events;
    private final GuestService guests;
    private final WhatsAppMessages whatsApp;
    private final MessageService messages;
    private final AppProperties props;

    public HostController(EventService events, GuestService guests, WhatsAppMessages whatsApp,
                          MessageService messages, AppProperties props) {
        this.events = events;
        this.guests = guests;
        this.whatsApp = whatsApp;
        this.messages = messages;
        this.props = props;
    }

    @GetMapping
    String dashboard(@PathVariable String hostToken, Model model) {
        Event event = event(hostToken);
        List<Guest> list = guests.forEvent(event.getId());
        model.addAttribute("event", event);
        model.addAttribute("guests", list);
        model.addAttribute("totals", Totals.of(list));
        return "host/dashboard";
    }

    @GetMapping(value = "/guests.csv")
    ResponseEntity<byte[]> csv(@PathVariable String hostToken) {
        Event event = event(hostToken);
        // BOM so Excel opens Malayalam names as UTF-8.
        byte[] body = ("﻿" + GuestCsv.write(guests.forEvent(event.getId()))).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("guests.csv").build().toString())
                .body(body);
    }

    @GetMapping("/send")
    String sendScreen(@PathVariable String hostToken, Model model) {
        Event event = event(hostToken);
        List<Guest> list = guests.forEvent(event.getId());
        model.addAttribute("event", event);
        model.addAttribute("guests", list);
        model.addAttribute("totals", Totals.of(list));
        model.addAttribute("next", guests.nextUnsent(event.getId()).orElse(null));
        model.addAttribute("composer", messages.composer(event));
        // For the live preview only; each guest's real link is filled in when sending.
        model.addAttribute("sampleLink", props.publicBaseUrl() + "/i/…");
        return "host/send";
    }

    /**
     * Saves the composer as shown on screen (when posted along), marks the guest as sent, and hands the
     * browser over to WhatsApp, so the phone number never reaches the page.
     */
    @PostMapping("/send/{guestId}")
    ResponseEntity<String> send(@PathVariable String hostToken, @PathVariable long guestId,
                @RequestParam(required = false) String templateEn,
                @RequestParam(required = false) String templateMl,
                @RequestParam(required = false) MessageLanguage language) {
        Event event = event(hostToken);
        Composer composer;
        try {
            composer = language == null ? messages.composer(event) : messages.save(event, templateEn, templateMl, language);
        } catch (InvalidMessageException e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
        Guest guest = guests.markSent(event.getId(), guestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String url = WhatsAppMessages.waMeUrl(guest.getPhone(), whatsApp.render(composer.active(), guest));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** htmx: save the composer without sending. Replies with a status line. */
    @PostMapping("/composer")
    String saveComposer(@PathVariable String hostToken, @RequestParam String templateEn,
                        @RequestParam String templateMl, @RequestParam MessageLanguage language, Model model) {
        Event event = event(hostToken);
        try {
            messages.save(event, templateEn, templateMl, language);
            model.addAttribute("saved", true);
        } catch (InvalidMessageException e) {
            model.addAttribute("composerError", e.getMessage());
        }
        return "host/send :: composer-status";
    }

    @PostMapping("/composer/reset")
    String resetComposer(@PathVariable String hostToken) {
        Event event = event(hostToken);
        messages.reset(event);
        return "redirect:/h/" + hostToken + "/send";
    }

    /** Undo for "sent": the guest goes back to the not-sent list. */
    @PostMapping("/guests/{guestId}/unsend")
    ResponseEntity<Void> unsend(@PathVariable String hostToken, @PathVariable long guestId) {
        Event event = event(hostToken);
        return guests.markUnsent(event.getId(), guestId)
                .map(g -> ResponseEntity.noContent().<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Event event(String hostToken) {
        return events.findByHostToken(hostToken).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
