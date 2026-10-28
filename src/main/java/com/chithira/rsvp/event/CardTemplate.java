package com.chithira.rsvp.event;

/** The animated card designs available on the public invite page. */
public enum CardTemplate {
    MARIGOLD("Marigold", "Warm ivory and gold with falling petals"),
    MOONLIGHT("Moonlight", "Midnight blue with twinkling stars");

    private final String title;
    private final String description;

    CardTemplate(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String cssClass() {
        return "tpl-" + name().toLowerCase();
    }
}
