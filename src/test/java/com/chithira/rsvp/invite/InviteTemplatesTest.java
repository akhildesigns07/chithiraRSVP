package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.guest.Guest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class InviteTemplatesTest {

    /** Art that identifies each design; all of it inline SVG. */
    private static final Map<CardTemplate, List<String>> SIGNATURE_ART = Map.of(
            CardTemplate.KERALA_TRADITIONAL, List.of("class=\"motif lamp\"", "aria-label=\"Nilavilakku (traditional lamp)\"", "class=\"flame\""),
            CardTemplate.CHRISTIAN_ELEGANT, List.of("id=\"ce-corner\"", "class=\"corner c-tl\"", "class=\"corner c-br\"", "class=\"motif cross\""),
            CardTemplate.NIKAH_CLASSIC, List.of("id=\"nc-lattice\"", "class=\"motif arch\"", "<pattern"));

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    EventRepository events;

    @Autowired
    com.chithira.rsvp.event.LetterPageRepository letters;

    private Guest guestWith(CardTemplate template) {
        var event = data.event();
        letters.save(new com.chithira.rsvp.event.LetterPage(event.getId(), 1, "application/pdf", "l.pdf", TestData.PDF));
        event.setTemplate(template);
        events.save(event);
        return data.guest(event, "Anil Kumar", "919876543210");
    }

    private String html(Guest guest) throws Exception {
        return mvc.perform(get("/i/{t}", guest.getToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @EnumSource(CardTemplate.class)
    void eachDesignRendersItsOwnArtAndAllContent(CardTemplate template) throws Exception {
        String html = html(guestWith(template));

        assertThat(html).contains("class=\"invite " + template.cssClass() + "\"");
        assertThat(html).contains(SIGNATURE_ART.get(template));
        assertThat(html).contains("e-top", "e-right", "e-bottom", "e-left");
        // Other designs' art is not sent.
        SIGNATURE_ART.forEach((other, art) -> {
            if (other != template) {
                assertThat(html).doesNotContain(art.getFirst());
            }
        });
        assertThat(html).contains("Dear Anil Kumar,", "Anjali &amp; Rahul", "Sunday, 14 February 2027", "St. Mary&#39;s Church");
        assertThat(html).doesNotContain("9876543210", "98765 43210");
    }

    @ParameterizedTest
    @EnumSource(CardTemplate.class)
    void stagesAppearInTheRequiredOrder(CardTemplate template) throws Exception {
        String html = html(guestWith(template));
        int greeting = html.indexOf("stage s-greeting");
        int names = html.indexOf("stage s-names");
        int details = html.indexOf("details stage s-details");
        int rsvp = html.indexOf("stage s-rsvp");
        assertThat(greeting).isPositive();
        assertThat(names).isGreaterThan(greeting);
        assertThat(details).isGreaterThan(names);
        assertThat(rsvp).isGreaterThan(details);
        assertThat(html.indexOf("id=\"rsvp\"")).isGreaterThan(rsvp);
        // Each piece is included exactly once.
        for (String piece : List.of("stage s-greeting", "stage s-names", "details stage s-details", "id=\"rsvp\"",
                "class=\"rsvp-form\"", "<details", "View full invitation letter")) {
            assertThat(html.split(Pattern.quote(piece), -1)).as(piece).hasSize(2);
        }
    }

    @ParameterizedTest
    @EnumSource(CardTemplate.class)
    void onlyTheEventPhotoIsAnImageAndOnlyGoogleFontsIsExternal(CardTemplate template) throws Exception {
        var guest = guestWith(template);
        String html = html(guest);

        assertThat(html).doesNotContain("<img");
        // SVG paint servers (url(#id)) are inline; anything else would be an external asset.
        assertThat(html).doesNotContainPattern("url\\((?!#)");
        List<String> external = new ArrayList<>();
        Matcher m = Pattern.compile("(?:src|href)=\"(https?://[^\"]+)\"").matcher(html);
        while (m.find()) {
            external.add(m.group(1));
        }
        assertThat(external).allSatisfy(url -> assertThat(url).matches(
                "https://fonts\\.(googleapis|gstatic)\\.com.*|https://maps\\.app\\.goo\\.gl/.*"));
        assertThat(html).contains("family=Noto+Serif+Malayalam");
        assertThat(html).doesNotContain("Noto+Sans", "Cormorant", "Inter");
    }

    @Test
    void localPageWeightLeavesRoomForFontsWithinTheBudget() throws Exception {
        long heaviestHtml = 0;
        long heaviestHtmlGzip = 0;
        for (CardTemplate t : CardTemplate.values()) {
            byte[] page = html(guestWith(t)).getBytes();
            heaviestHtml = Math.max(heaviestHtml, page.length);
            heaviestHtmlGzip = Math.max(heaviestHtmlGzip, gzip(page));
        }
        byte[] css = mvc.perform(get("/css/invite.css")).andReturn().getResponse().getContentAsByteArray();
        byte[] htmx = mvc.perform(get("/webjars/htmx.org/2.0.4/dist/htmx.min.js")).andReturn().getResponse().getContentAsByteArray();

        long raw = heaviestHtml + css.length + htmx.length;
        long transferred = heaviestHtmlGzip + gzip(css) + gzip(htmx);
        // Noto Serif Malayalam (Malayalam + Latin subsets, variable weight) is ~88 KB from Google Fonts.
        // Everything we serve must leave the page, without the photo, under 200 KB on the wire.
        assertThat(transferred).as("compressed HTML + CSS + JS").isLessThan(40 * 1024);
        assertThat(transferred + 95 * 1024).isLessThan(200 * 1024);
        assertThat(raw).as("uncompressed HTML + CSS + JS").isLessThan(120 * 1024);
    }

    private static long gzip(byte[] bytes) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var gz = new GZIPOutputStream(out)) {
            gz.write(bytes);
        }
        return out.size();
    }
}
