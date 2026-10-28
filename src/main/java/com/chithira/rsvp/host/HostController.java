package com.chithira.rsvp.host;

import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventService;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestService;
import com.chithira.rsvp.util.Tokens;
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
import org.springframework.web.server.ResponseStatusException;

/** Host dashboard, reachable with the event's host token. Phone numbers never appear in its pages or export. */
@Controller
@RequestMapping("/h/{hostToken}")
public class HostController {

    private final EventService events;
    private final GuestService guests;
    private final WhatsAppMessages whatsApp;

    public HostController(EventService events, GuestService guests, WhatsAppMessages whatsApp) {
        this.events = events;
        this.guests = guests;
        this.whatsApp = whatsApp;
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
        model.addAttribute("current", guests.nextUnsent(event.getId()).map(Guest::getId).orElse(null));
        return "host/send";
    }

    /** Marks the guest as sent and hands the browser over to WhatsApp, so the phone number never reaches the page. */
    @PostMapping("/send/{guestId}")
    String send(@PathVariable String hostToken, @PathVariable long guestId) {
        Event event = event(hostToken);
        Guest guest = guests.markSent(event.getId(), guestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return "redirect:" + whatsApp.waMeUrl(guest, event);
    }

    private Event event(String hostToken) {
        if (!Tokens.looksValid(hostToken)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return events.findByHostToken(hostToken).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
