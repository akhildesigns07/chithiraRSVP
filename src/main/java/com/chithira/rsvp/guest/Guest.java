package com.chithira.rsvp.guest;

import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.util.Tokens;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "guest")
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false, updatable = false)
    private String token = Tokens.newToken();

    /** Lookup key for {@link #token}; see {@link Tokens}. */
    @Column(nullable = false, updatable = false)
    private String tokenHash = Tokens.hash(token);

    @Column(nullable = false)
    private String name;

    /** Digits only, with country code (e.g. 919876543210) — the form wa.me expects. */
    @Column(nullable = false)
    private String phone;

    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;

    private Integer headcount;

    private LocalDateTime respondedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Guest() {
    }

    public Guest(Event event, String name, String phone) {
        this.event = event;
        this.name = name;
        this.phone = phone;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markSent() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }

    public void markUnsent() {
        sentAt = null;
    }

    public void attend(int headcount) {
        this.rsvpStatus = RsvpStatus.ATTENDING;
        this.headcount = headcount;
        this.respondedAt = LocalDateTime.now();
    }

    public void decline() {
        this.rsvpStatus = RsvpStatus.NOT_ATTENDING;
        this.headcount = null;
        this.respondedAt = LocalDateTime.now();
    }

    public boolean isSent() {
        return sentAt != null;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getToken() {
        return token;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    /** Formatted for staff screens only; phone numbers never appear on token pages. */
    public String getPhoneDisplay() {
        return PhoneNormalizer.format(phone);
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public RsvpStatus getRsvpStatus() {
        return rsvpStatus;
    }

    public Integer getHeadcount() {
        return headcount;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }
}
