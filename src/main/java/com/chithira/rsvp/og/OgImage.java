package com.chithira.rsvp.og;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cached share image for an event, valid while {@link #version} matches the event's last update. */
@Entity
@Table(name = "event_og_image")
public class OgImage {

    @Id
    private Long eventId;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private byte[] jpeg;

    protected OgImage() {
    }

    public OgImage(Long eventId, long version, byte[] jpeg) {
        this.eventId = eventId;
        this.version = version;
        this.jpeg = jpeg;
    }

    public long getVersion() {
        return version;
    }

    public byte[] getJpeg() {
        return jpeg;
    }

    void replace(long version, byte[] jpeg) {
        this.version = version;
        this.jpeg = jpeg;
    }
}
