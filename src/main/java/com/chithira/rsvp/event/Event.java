package com.chithira.rsvp.event;

import com.chithira.rsvp.util.Tokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String hostToken = Tokens.newToken();

    @Column(nullable = false)
    private String coupleNames;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    /** Local time at the venue. */
    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private String venue;

    private String mapsUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardTemplate template;

    @Column(nullable = false)
    private String messageEn;

    @Column(nullable = false)
    private String messageMl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getHostToken() {
        return hostToken;
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
        this.mapsUrl = mapsUrl;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
