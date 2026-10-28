package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.EventPhoto;
import com.chithira.rsvp.event.EventPhotoRepository;
import com.chithira.rsvp.event.LetterPage;
import com.chithira.rsvp.event.LetterPageRepository;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestRepository;
import com.chithira.rsvp.guest.RsvpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class InviteControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    GuestRepository guests;
    @Autowired
    EventPhotoRepository photos;
    @Autowired
    LetterPageRepository letters;

    Guest guest;

    @BeforeEach
    void setUp() {
        guest = data.guest(data.event(), "Anil Kumar", "919876543210");
    }

    @Test
    void showsPersonalisedCardWithoutLogin() throws Exception {
        mvc.perform(get("/i/{t}", guest.getToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(content().string(allOf(
                        containsString("Dear Anil Kumar,"),
                        containsString("Anjali &amp; Rahul"),
                        containsString("St. Mary&#39;s Church, Kottayam"),
                        containsString("Sunday, 14 February 2027"),
                        containsString("10:30 AM"),
                        containsString("https://maps.app.goo.gl/abc123"),
                        containsString("tpl-marigold"),
                        containsString("Noto+Sans+Malayalam"),
                        containsString("വിവാഹം"))));
    }

    @Test
    void neverShowsPhoneNumbers() throws Exception {
        String html = mvc.perform(get("/i/{t}", guest.getToken())).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("9876543210").doesNotContain("98765 43210");
    }

    @Test
    void unknownOrMalformedTokensAre404() throws Exception {
        mvc.perform(get("/i/{t}", "AAAAAAAAAAAAAAAAAAAAAA")).andExpect(status().isNotFound());
        mvc.perform(get("/i/{t}", "not-a-token")).andExpect(status().isNotFound());
    }

    @Test
    void attendingWithHeadcountViaHtmx() throws Exception {
        mvc.perform(post("/i/{t}/rsvp", guest.getToken())
                        .header("HX-Request", "true")
                        .param("attending", "yes")
                        .param("headcount", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("all 3 of you"),
                        not(containsString("<html")))));

        var saved = guests.findById(guest.getId()).orElseThrow();
        assertThat(saved.getRsvpStatus()).isEqualTo(RsvpStatus.ATTENDING);
        assertThat(saved.getHeadcount()).isEqualTo(3);
        assertThat(saved.getRespondedAt()).isNotNull();
    }

    @Test
    void notAttendingClearsHeadcountAndWorksWithoutJavascript() throws Exception {
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).param("attending", "yes").param("headcount", "2"));
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).param("attending", "no").param("headcount", "2"))
                .andExpect(redirectedUrl("/i/" + guest.getToken() + "#rsvp"));

        var saved = guests.findById(guest.getId()).orElseThrow();
        assertThat(saved.getRsvpStatus()).isEqualTo(RsvpStatus.NOT_ATTENDING);
        assertThat(saved.getHeadcount()).isNull();
    }

    @Test
    void rejectsInvalidHeadcountOrChoice() throws Exception {
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).header("HX-Request", "true")
                        .param("attending", "yes").param("headcount", "0"))
                .andExpect(content().string(containsString("Please choose an option")));
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).header("HX-Request", "true")
                        .param("attending", "yes").param("headcount", "21"))
                .andExpect(content().string(containsString("Please choose an option")));
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).header("HX-Request", "true"))
                .andExpect(content().string(containsString("Please choose an option")));

        assertThat(guests.findById(guest.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
    }

    @Test
    void letterButtonOnlyAppearsWhenALetterIsUploaded() throws Exception {
        mvc.perform(get("/i/{t}", guest.getToken()))
                .andExpect(content().string(not(containsString("View full invitation letter"))));
        mvc.perform(get("/i/{t}/letter", guest.getToken())).andExpect(status().isNotFound());

        letters.save(new LetterPage(guest.getEvent().getId(), 1, "application/pdf", "letter.pdf", TestData.PDF));

        mvc.perform(get("/i/{t}", guest.getToken()))
                .andExpect(content().string(allOf(
                        containsString("View full invitation letter"),
                        containsString("/i/" + guest.getToken() + "/letter"))));
    }

    @Test
    void showsPdfLetterWithDownload() throws Exception {
        letters.save(new LetterPage(guest.getEvent().getId(), 1, "application/pdf", "letter.pdf", TestData.PDF));

        mvc.perform(get("/i/{t}/letter", guest.getToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(content().string(allOf(
                        containsString("type=\"application/pdf\""),
                        containsString("/i/" + guest.getToken() + "/letter/1?download=true"))));
        mvc.perform(get("/i/{t}/letter/1", guest.getToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        mvc.perform(get("/i/{t}/letter/1", guest.getToken()).param("download", "true"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
        mvc.perform(get("/i/{t}/letter/2", guest.getToken())).andExpect(status().isNotFound());
    }

    @Test
    void showsImageLetterPagesInOrder() throws Exception {
        long eventId = guest.getEvent().getId();
        letters.save(new LetterPage(eventId, 2, "image/png", "p2.png", TestData.PNG));
        letters.save(new LetterPage(eventId, 1, "image/png", "p1.png", TestData.PNG));

        String html = mvc.perform(get("/i/{t}/letter", guest.getToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Page 1 of 2").contains("Page 2 of 2");
        assertThat(html.indexOf("/letter/1")).isLessThan(html.indexOf("/letter/2"));
        assertThat(html).doesNotContain("9876543210");
    }

    @Test
    void servesEventPhotoByGuestToken() throws Exception {
        mvc.perform(get("/i/{t}/photo", guest.getToken())).andExpect(status().isNotFound());

        photos.save(new EventPhoto(guest.getEvent().getId(), "image/png", TestData.PNG));
        mvc.perform(get("/i/{t}/photo", guest.getToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/i/{t}", guest.getToken()))
                .andExpect(content().string(containsString("/i/" + guest.getToken() + "/photo")));
    }
}
