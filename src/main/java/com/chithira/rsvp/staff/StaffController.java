package com.chithira.rsvp.staff;

import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventForm;
import com.chithira.rsvp.event.EventService;
import com.chithira.rsvp.event.EventService.Uploads;
import com.chithira.rsvp.event.EventType;
import com.chithira.rsvp.event.InvalidUploadException;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestListParser;
import com.chithira.rsvp.guest.GuestService;
import com.chithira.rsvp.host.Totals;
import com.chithira.rsvp.invite.InviteCardModel;
import com.chithira.rsvp.util.FileResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/portal")
public class StaffController {

    private final EventService events;
    private final GuestService guests;

    public StaffController(EventService events, GuestService guests) {
        this.events = events;
        this.guests = guests;
    }

    @ModelAttribute
    void options(Model model, @PathVariable(name = "id", required = false) Long id) {
        model.addAttribute("pageTitle", id == null ? "New event" : "Edit event");
        model.addAttribute("navActive", id == null ? "new" : "events");
        model.addAttribute("eventTypes", EventType.values());
        model.addAttribute("templates", CardTemplate.values());
    }

    @GetMapping
    String list(Model model) {
        List<Event> all = events.listAll();
        Map<Long, Totals> totals = all.stream()
                .collect(Collectors.toMap(Event::getId, e -> Totals.of(guests.forEvent(e.getId()))));
        model.addAttribute("events", all);
        model.addAttribute("totals", totals);
        model.addAttribute("navActive", "events");
        return "portal/events";
    }

    @GetMapping("/events/new")
    String newEvent(Model model) {
        model.addAttribute("form", new EventForm());
        return "portal/event-form";
    }

    @PostMapping("/events")
    String create(@Valid @ModelAttribute("form") EventForm form, BindingResult binding,
                  @RequestParam(name = "photo", required = false) MultipartFile photo,
                  @RequestParam(name = "letter", required = false) List<MultipartFile> letter) {
        if (binding.hasErrors()) {
            return "portal/event-form";
        }
        try {
            Event event = events.create(form, new Uploads(photo, letter, false));
            return "redirect:/portal/events/" + event.getId();
        } catch (InvalidUploadException e) {
            binding.reject("upload", e.getMessage());
            return "portal/event-form";
        }
    }

    @GetMapping("/events/{id}")
    String detail(@PathVariable long id, Model model) {
        addDetailModel(id, model);
        model.addAttribute("pasteText", "");
        return "portal/event";
    }

    @GetMapping("/events/{id}/edit")
    String edit(@PathVariable long id, Model model) {
        Event event = events.get(id);
        addFormModel(event, model);
        model.addAttribute("form", EventForm.from(event));
        return "portal/event-form";
    }

    @PostMapping("/events/{id}")
    String update(@PathVariable long id, @Valid @ModelAttribute("form") EventForm form, BindingResult binding,
                  @RequestParam(name = "photo", required = false) MultipartFile photo,
                  @RequestParam(name = "letter", required = false) List<MultipartFile> letter,
                  @RequestParam(name = "removeLetter", defaultValue = "false") boolean removeLetter,
                  Model model) {
        if (!binding.hasErrors()) {
            try {
                events.update(id, form, new Uploads(photo, letter, removeLetter));
                return "redirect:/portal/events/" + id;
            } catch (InvalidUploadException e) {
                binding.reject("upload", e.getMessage());
            }
        }
        addFormModel(events.get(id), model);
        return "portal/event-form";
    }

    @GetMapping("/events/{id}/photo")
    ResponseEntity<byte[]> photo(@PathVariable long id) {
        return FileResponses.photo(events.photo(id), FileResponses.STAFF);
    }

    @GetMapping("/events/{id}/letter/{page}")
    ResponseEntity<byte[]> letterPage(@PathVariable long id, @PathVariable int page) {
        return FileResponses.letterPage(events.letterPage(id, page), false, FileResponses.STAFF);
    }

    /** The real invitation card with a sample guest, so staff can show customers each design. */
    @GetMapping("/events/{id}/preview")
    String preview(@PathVariable long id, @RequestParam(name = "template", required = false) CardTemplate template,
                   Model model) {
        Event event = events.get(id);
        InviteCardModel.forPreview(model, event, template == null ? event.getTemplate() : template,
                events.hasPhoto(id), !events.letterPages(id).isEmpty());
        return "invite/card";
    }

    /** htmx endpoint: re-renders the guest section with either the updated list or the paste errors. */
    @PostMapping("/events/{id}/guests")
    String addGuests(@PathVariable long id, @RequestParam("guests") String text, Model model) {
        GuestListParser.Result result = guests.addFromPaste(events.get(id), text);
        addDetailModel(id, model);
        model.addAttribute("pasteErrors", result.errors());
        model.addAttribute("pasteText", result.hasErrors() ? text : "");
        model.addAttribute("addedCount", result.guests().size());
        return "portal/event :: guests";
    }

    /** htmx (or plain form) endpoint: removes one guest of this event and re-renders the guest section. */
    @PostMapping("/events/{id}/guests/{guestId}/delete")
    String removeGuest(@PathVariable long id, @PathVariable long guestId,
                       @RequestHeader(name = "HX-Request", required = false) String htmx, Model model) {
        Event event = events.get(id);
        if (!guests.remove(event.getId(), guestId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (htmx == null) {
            return "redirect:/portal/events/" + id + "#guests";
        }
        addDetailModel(id, model);
        model.addAttribute("pasteText", "");
        model.addAttribute("removedGuest", true);
        return "portal/event :: guests";
    }

    private void addFormModel(Event event, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("hasPhoto", events.hasPhoto(event.getId()));
        model.addAttribute("letterPages", events.letterPages(event.getId()));
    }

    private void addDetailModel(long id, Model model) {
        Event event = events.get(id);
        List<Guest> list = guests.forEvent(id);
        addFormModel(event, model);
        model.addAttribute("guests", list);
        model.addAttribute("totals", Totals.of(list));
    }
}
