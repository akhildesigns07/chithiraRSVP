package com.chithira.rsvp.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Kept in its own table so photo bytes are only loaded when the image itself is requested. */
@Entity
@Table(name = "event_photo")
public class EventPhoto {

    @Id
    private Long eventId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    protected EventPhoto() {
    }

    public EventPhoto(Long eventId, String contentType, byte[] data) {
        this.eventId = eventId;
        this.contentType = contentType;
        this.data = data;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
    }
}
