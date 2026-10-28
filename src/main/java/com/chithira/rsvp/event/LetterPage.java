package com.chithira.rsvp.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One page of the uploaded invitation letter (or the whole letter, when it is a PDF). */
@Entity
@Table(name = "event_letter_page")
public class LetterPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false)
    private int pageNo;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private byte[] data;

    protected LetterPage() {
    }

    public LetterPage(Long eventId, int pageNo, String contentType, String filename, byte[] data) {
        this.eventId = eventId;
        this.pageNo = pageNo;
        this.contentType = contentType;
        this.filename = filename;
        this.data = data;
    }

    public int getPageNo() {
        return pageNo;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilename() {
        return filename;
    }

    public byte[] getData() {
        return data;
    }
}
