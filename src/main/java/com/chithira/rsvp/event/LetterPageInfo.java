package com.chithira.rsvp.event;

/** Letter page metadata without the file bytes. */
public record LetterPageInfo(int pageNo, String contentType, String filename) {

    public boolean isPdf() {
        return "application/pdf".equals(contentType);
    }
}
