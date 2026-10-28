package com.chithira.rsvp.event;

public enum EventType {
    WEDDING("Wedding", "വിവാഹം"),
    ENGAGEMENT("Engagement", "വിവാഹ നിശ്ചയം"),
    RECEPTION("Reception", "വിവാഹ സൽക്കാരം");

    private final String labelEn;
    private final String labelMl;

    EventType(String labelEn, String labelMl) {
        this.labelEn = labelEn;
        this.labelMl = labelMl;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getLabelMl() {
        return labelMl;
    }
}
