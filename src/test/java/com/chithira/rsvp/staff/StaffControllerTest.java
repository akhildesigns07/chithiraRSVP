package com.chithira.rsvp.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.EventPhotoRepository;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.event.LetterPage;
import com.chithira.rsvp.event.LetterPageInfo;
import com.chithira.rsvp.event.LetterPageRepository;
import com.chithira.rsvp.guest.GuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@IntegrationTest
@WithMockUser(roles = "STAFF")
class StaffControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    EventRepository events;
    @Autowired
    EventPhotoRepository photos;
    @Autowired
    GuestRepository guests;
    @Autowired
    LetterPageRepository letters;

    private MockMultipartHttpServletRequestBuilder eventForm(String url, MockMultipartFile photo) {
        return eventForm(url, photo, "https://maps.app.goo.gl/xyz");
    }

    private MockMultipartHttpServletRequestBuilder eventForm(String url, MockMultipartFile photo, String mapsUrl) {
        var req = multipart(url);
        if (photo != null) {
            req.file(photo);
        }
        req.param("coupleNames", "Meera & Arjun")
                .param("eventType", "ENGAGEMENT")
                .param("startsAt", "2027-01-10T18:00")
                .param("venue", "Lulu Convention Centre, Thrissur")
                .param("mapsUrl", mapsUrl)
                .param("template", "NIKAH_CLASSIC")
                .param("messageEn", "Join us")
                .param("messageMl", "സ്വാഗതം")
                .with(csrf());
        return req;
    }

    @Test
    void createsEventWithPhoto() throws Exception {
        var photo = new MockMultipartFile("photo", "p.png", "image/png", TestData.PNG);

        mvc.perform(eventForm("/portal/events", photo))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/portal/events/*"));

        var saved = events.findAll().getFirst();
        assertThat(saved.getCoupleNames()).isEqualTo("Meera & Arjun");
        assertThat(saved.getTemplate()).isEqualTo(CardTemplate.NIKAH_CLASSIC);
        assertThat(saved.getHostToken()).hasSize(22);
        assertThat(photos.findById(saved.getId())).get()
                .satisfies(p -> assertThat(p.getContentType()).isEqualTo("image/png"));
    }

    @Test
    void rejectsPhotoOverOneMegabyte() throws Exception {
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(TestData.PNG, 0, big, 0, TestData.PNG.length);
        var photo = new MockMultipartFile("photo", "big.png", "image/png", big);

        mvc.perform(eventForm("/portal/events", photo))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 MB or smaller")));
        assertThat(events.count()).isZero();
    }

    @Test
    void acceptsPhotoOfExactlyOneMegabyte() throws Exception {
        byte[] exact = new byte[1024 * 1024];
        System.arraycopy(TestData.PNG, 0, exact, 0, TestData.PNG.length);

        mvc.perform(eventForm("/portal/events", new MockMultipartFile("photo", "ok.png", "image/png", exact)))
                .andExpect(status().is3xxRedirection());
        assertThat(photos.count()).isEqualTo(1);
    }

    @Test
    void oversizedPhotoOnEditKeepsTheExistingPhotoAndEvent() throws Exception {
        var event = data.event();
        photos.save(new com.chithira.rsvp.event.EventPhoto(event.getId(), "image/png", TestData.PNG));
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(TestData.PNG, 0, big, 0, TestData.PNG.length);

        mvc.perform(eventForm("/portal/events/" + event.getId(), new MockMultipartFile("photo", "big.png", "image/png", big)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1 MB or smaller")));

        assertThat(photos.findById(event.getId()).orElseThrow().getData()).isEqualTo(TestData.PNG);
        assertThat(events.findById(event.getId()).orElseThrow().getCoupleNames()).isEqualTo("Anjali & Rahul");
    }

    @ParameterizedTest
    @CsvSource({
            "x.gif, image/gif, GIF89a....",
            "x.svg, image/svg+xml, <svg xmlns='http://www.w3.org/2000/svg'/>",
            "x.html, image/jpeg, <html><script>alert(1)</script>",
            "x.pdf, image/png, %PDF-1.4",
    })
    void rejectsAnythingButJpegPngOrWebp(String name, String declaredType, String body) throws Exception {
        mvc.perform(eventForm("/portal/events", new MockMultipartFile("photo", name, declaredType, body.getBytes())))
                .andExpect(content().string(containsString("JPEG, PNG or WebP")));
        assertThat(events.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "image/jpeg, FFD8FFE000104A464946",
            "image/webp, 524946460000000057454250",
    })
    void acceptsJpegAndWebpBySignature(String expectedType, String hex) throws Exception {
        byte[] bytes = java.util.HexFormat.of().parseHex(hex);
        mvc.perform(eventForm("/portal/events", new MockMultipartFile("photo", "p", "application/octet-stream", bytes)))
                .andExpect(status().is3xxRedirection());
        assertThat(photos.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getContentType()).isEqualTo(expectedType));
    }

    @Test
    void rejectsNonImagePhotoEvenIfLabelledAsImage() throws Exception {
        var photo = new MockMultipartFile("photo", "x.png", "image/png", "<svg></svg>".getBytes());

        mvc.perform(eventForm("/portal/events", photo))
                .andExpect(content().string(containsString("JPEG, PNG or WebP")));
        assertThat(events.count()).isZero();
    }

    @Test
    void rejectsNonGoogleMapsLink() throws Exception {
        mvc.perform(eventForm("/portal/events", null, "javascript:alert(1)"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Google Maps link")));
        assertThat(events.count()).isZero();
    }

    @Test
    void editsEventAndKeepsPhotoWhenNoneUploaded() throws Exception {
        var event = data.event();
        photos.save(new com.chithira.rsvp.event.EventPhoto(event.getId(), "image/png", TestData.PNG));

        mvc.perform(get("/portal/events/{id}/edit", event.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anjali &amp; Rahul")));

        mvc.perform(eventForm("/portal/events/" + event.getId(), null))
                .andExpect(status().is3xxRedirection());

        assertThat(events.findById(event.getId()).orElseThrow().getCoupleNames()).isEqualTo("Meera & Arjun");
        assertThat(photos.existsById(event.getId())).isTrue();
    }

    @Test
    void uploadsPdfLetterAndShowsItOnTheEventPage() throws Exception {
        var pdf = new MockMultipartFile("letter", "Wedding Letter.pdf", "application/pdf", TestData.PDF);

        mvc.perform(eventForm("/portal/events", null).file(pdf)).andExpect(status().is3xxRedirection());

        var event = events.findAll().getFirst();
        assertThat(letters.findInfoByEventId(event.getId()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.isPdf()).isTrue();
                    assertThat(p.filename()).isEqualTo("Wedding Letter.pdf");
                });
        mvc.perform(get("/portal/events/{id}", event.getId()))
                .andExpect(content().string(containsString("Wedding Letter.pdf")));
        mvc.perform(get("/portal/events/{id}/letter/1", event.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void replacesLetterWithPageImagesAndCanRemoveIt() throws Exception {
        var event = data.event();
        letters.save(new LetterPage(event.getId(), 1, "application/pdf", "old.pdf", TestData.PDF));

        mvc.perform(eventForm("/portal/events/" + event.getId(), null)
                        .file(new MockMultipartFile("letter", "p1.png", "image/png", TestData.PNG))
                        .file(new MockMultipartFile("letter", "p2.png", "image/png", TestData.PNG)))
                .andExpect(status().is3xxRedirection());
        assertThat(letters.findInfoByEventId(event.getId()))
                .extracting(LetterPageInfo::pageNo, LetterPageInfo::filename)
                .containsExactly(tuple(1, "p1.png"), tuple(2, "p2.png"));

        // Saving without new files keeps the letter.
        mvc.perform(eventForm("/portal/events/" + event.getId(), null)).andExpect(status().is3xxRedirection());
        assertThat(letters.findInfoByEventId(event.getId())).hasSize(2);

        mvc.perform(eventForm("/portal/events/" + event.getId(), null).param("removeLetter", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(letters.findInfoByEventId(event.getId())).isEmpty();
    }

    @Test
    void rejectsMixedPdfAndImagesOrOversizedLetter() throws Exception {
        mvc.perform(eventForm("/portal/events", null)
                        .file(new MockMultipartFile("letter", "a.pdf", "application/pdf", TestData.PDF))
                        .file(new MockMultipartFile("letter", "b.png", "image/png", TestData.PNG)))
                .andExpect(content().string(containsString("either one PDF or page images")));

        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(TestData.PDF, 0, big, 0, TestData.PDF.length);
        mvc.perform(eventForm("/portal/events", null)
                        .file(new MockMultipartFile("letter", "big.pdf", "application/pdf", big)))
                .andExpect(content().string(containsString("10 MB or smaller")));

        mvc.perform(eventForm("/portal/events", null)
                        .file(new MockMultipartFile("letter", "x.pdf", "application/pdf", "<html>".getBytes())))
                .andExpect(content().string(containsString("must be a PDF or JPEG")));

        assertThat(events.count()).isZero();
    }

    @Test
    void portalPagesRender() throws Exception {
        var event = data.event();
        data.guest(event, "Anil", "919876543210");

        mvc.perform(get("/portal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anjali &amp; Rahul")));
        mvc.perform(get("/portal/events/new")).andExpect(status().isOk());
        mvc.perform(get("/portal/events/{id}", event.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("+91 98765 43210"),
                        containsString("https://invite.test/h/" + event.getHostToken()))));
    }

    @ParameterizedTest
    @EnumSource(CardTemplate.class)
    void previewRendersTheRealCardForAnyDesignWithRepliesOff(CardTemplate template) throws Exception {
        var event = data.event();
        photos.save(new com.chithira.rsvp.event.EventPhoto(event.getId(), "image/png", TestData.PNG));

        String html = mvc.perform(get("/portal/events/{id}/preview", event.getId()).param("template", template.name()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("class=\"invite " + template.cssClass() + "\"", "Dear Guest Name,", "Anjali &amp; Rahul",
                "Preview: replies are switched off.", "/portal/events/" + event.getId() + "/photo");
        assertThat(html).doesNotContain("/i/", "hx-post=");
        assertThat(html).containsPattern("<button type=\"submit\" class=\"btn btn-solid\" disabled");
    }

    @Test
    void previewDefaultsToTheEventsDesignAndRejectsUnknownOnes() throws Exception {
        var event = data.event();
        mvc.perform(get("/portal/events/{id}/preview", event.getId()))
                .andExpect(content().string(containsString("tpl-kerala-traditional")));
        mvc.perform(get("/portal/events/{id}/preview", event.getId()).param("template", "MARIGOLD"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/portal/events/{id}/preview", 9999)).andExpect(status().isNotFound());
    }

    @Test
    void eventPageOffersAPhoneSizedPreviewOfEveryDesign() throws Exception {
        var event = data.event();
        String html = mvc.perform(get("/portal/events/{id}", event.getId()))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<iframe name=\"card-preview\"", "width=\"360\"");
        for (CardTemplate t : CardTemplate.values()) {
            assertThat(html).contains("/portal/events/" + event.getId() + "/preview?template=" + t.name());
        }
    }

    @Test
    void pastesGuestListWithNormalisedNumbers() throws Exception {
        var event = data.event();

        mvc.perform(post("/portal/events/{id}/guests", event.getId())
                        .param("guests", "Anil, 98765 43210\nFathima, 050 123 4567")
                        .header("HX-Request", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Added 2 guest(s).")))
                .andExpect(content().string(containsString("+971 50 123 4567")))
                .andExpect(content().string(not(containsString("<html"))));

        assertThat(guests.findByEventIdOrderById(event.getId()))
                .extracting(g -> g.getPhone())
                .containsExactly("919876543210", "971501234567");
    }

    @Test
    void pasteWithAnyBadLineAddsNothing() throws Exception {
        var event = data.event();
        data.guest(event, "Existing", "919876543210");

        mvc.perform(post("/portal/events/{id}/guests", event.getId())
                        .param("guests", "New, 9876543211\nDup, +91 98765 43210\nBad, 123")
                        .with(csrf()))
                .andExpect(content().string(containsString("Nothing was added")))
                .andExpect(content().string(containsString("Line 2")))
                .andExpect(content().string(containsString("Already on the guest list")))
                .andExpect(content().string(containsString("Line 3")));

        assertThat(guests.findByEventIdOrderById(event.getId())).hasSize(1);
    }
}
