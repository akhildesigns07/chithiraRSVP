package com.chithira.rsvp.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public class EventForm {

    /** Google Maps share links: google.com/maps, maps.google.*, maps.app.goo.gl, goo.gl/maps. */
    static final String MAPS_URL_REGEX =
            "^$|^https://(www\\.)?(google\\.[a-z.]{2,6}/maps|maps\\.google\\.[a-z.]{2,6}/|maps\\.app\\.goo\\.gl/|goo\\.gl/maps/)\\S*$";

    @NotBlank
    @Size(max = 200)
    private String coupleNames;

    @NotNull
    private EventType eventType;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    @NotBlank
    @Size(max = 300)
    private String venue;

    @Size(max = 1000)
    @Pattern(regexp = MAPS_URL_REGEX, message = "must be a Google Maps link starting with https://")
    private String mapsUrl;

    @NotNull
    private CardTemplate template;

    @NotBlank
    @Size(max = 2000)
    private String messageEn;

    @NotBlank
    @Size(max = 2000)
    private String messageMl;

    public static EventForm from(Event e) {
        var f = new EventForm();
        f.coupleNames = e.getCoupleNames();
        f.eventType = e.getEventType();
        f.startsAt = e.getStartsAt();
        f.venue = e.getVenue();
        f.mapsUrl = e.getMapsUrl();
        f.template = e.getTemplate();
        f.messageEn = e.getMessageEn();
        f.messageMl = e.getMessageMl();
        return f;
    }

    void applyTo(Event e) {
        e.setCoupleNames(coupleNames.strip());
        e.setEventType(eventType);
        e.setStartsAt(startsAt);
        e.setVenue(venue.strip());
        e.setMapsUrl(mapsUrl == null || mapsUrl.isBlank() ? null : mapsUrl.strip());
        e.setTemplate(template);
        e.setMessageEn(messageEn.strip());
        e.setMessageMl(messageMl.strip());
    }

    public String getCoupleNames() {
        return coupleNames;
    }

    public void setCoupleNames(String coupleNames) {
        this.coupleNames = coupleNames;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getMapsUrl() {
        return mapsUrl;
    }

    public void setMapsUrl(String mapsUrl) {
        this.mapsUrl = mapsUrl == null ? null : mapsUrl.strip();
    }

    public CardTemplate getTemplate() {
        return template;
    }

    public void setTemplate(CardTemplate template) {
        this.template = template;
    }

    public String getMessageEn() {
        return messageEn;
    }

    public void setMessageEn(String messageEn) {
        this.messageEn = messageEn;
    }

    public String getMessageMl() {
        return messageMl;
    }

    public void setMessageMl(String messageMl) {
        this.messageMl = messageMl;
    }
}
