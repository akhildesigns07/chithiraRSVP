package com.chithira.rsvp.event;

/**
 * The animated card designs. Each has its own Thymeleaf fragment in
 * {@code templates/invite/designs/{slug}.html} and a {@code tpl-{slug}} block in invite.css.
 */
public enum CardTemplate {
    KERALA_TRADITIONAL("kerala-traditional", "Kerala Traditional", "Gold and deep maroon, kasavu border, nilavilakku"),
    CHRISTIAN_ELEGANT("christian-elegant", "Christian Elegant", "Ivory and gold, floral corners, a subtle cross"),
    NIKAH_CLASSIC("nikah-classic", "Nikah Classic", "Emerald and gold, arches and geometric pattern");

    private final String slug;
    private final String title;
    private final String description;

    CardTemplate(String slug, String title, String description) {
        this.slug = slug;
        this.title = title;
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String cssClass() {
        return "tpl-" + slug;
    }
}
