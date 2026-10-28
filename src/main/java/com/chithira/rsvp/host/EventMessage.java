package com.chithira.rsvp.host;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The host's WhatsApp message templates for an event. Kept apart from the event itself so editing
 * a message doesn't count as changing the event (which would redraw the share image).
 * A null template means "use the default", which follows later edits to the event's messages.
 */
@Entity
@Table(name = "event_message")
public class EventMessage {

    @Id
    private Long eventId;

    private String templateEn;

    private String templateMl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageLanguage language = MessageLanguage.EN;

    protected EventMessage() {
    }

    public EventMessage(Long eventId) {
        this.eventId = eventId;
    }

    public String getTemplateEn() {
        return templateEn;
    }

    public String getTemplateMl() {
        return templateMl;
    }

    public MessageLanguage getLanguage() {
        return language;
    }

    void update(String templateEn, String templateMl, MessageLanguage language) {
        this.templateEn = templateEn;
        this.templateMl = templateMl;
        this.language = language;
    }
}
