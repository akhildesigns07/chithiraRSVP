package com.chithira.rsvp.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class HostControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    GuestRepository guests;

    Event event;
    Guest anil;
    Guest fathima;
    Guest bob;

    @BeforeEach
    void setUp() {
        event = data.event();
        anil = data.guest(event, "Anil", "919876543210");
        fathima = data.guest(event, "Fathima", "971501234567");
        bob = data.guest(event, "=Bob, Jr.", "919876543211");
        anil.markSent();
        anil.attend(3);
        fathima.decline();
        guests.flush();
    }

    @Test
    void dashboardShowsTotalsWithoutLoginOrPhones() throws Exception {
        String html = mvc.perform(get("/h/{t}", event.getHostToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Anil"), containsString("Fathima"),
                        containsString("Total headcount"),
                        containsString("/h/" + event.getHostToken() + "/guests.csv"),
                        containsString("/h/" + event.getHostToken() + "/send"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("9876543210").doesNotContain("501234567");
        assertThat(html).containsPattern("<strong>3</strong><span>Invited");
        assertThat(html).containsPattern("<strong>1</strong><span>Sent");
        assertThat(html).containsPattern("<strong>1</strong><span>Attending");
        assertThat(html).containsPattern("<strong>3</strong><span>Total headcount");
        assertThat(html).containsPattern("<strong>1</strong><span>Not attending");
        assertThat(html).containsPattern("<strong>1</strong><span>No reply yet");
    }

    @Test
    void wrongHostTokenIs404OnEveryHostEndpoint() throws Exception {
        String host = event.getHostToken();
        String wrong = host.substring(0, 21) + (host.endsWith("A") ? "B" : "A");
        // A guest token is a valid token, but not for the host pages.
        for (String token : new String[] {wrong, "AAAAAAAAAAAAAAAAAAAAAA", "nope", anil.getToken()}) {
            mvc.perform(get("/h/{t}", token)).andExpect(status().isNotFound());
            mvc.perform(get("/h/{t}/guests.csv", token)).andExpect(status().isNotFound());
            mvc.perform(get("/h/{t}/send", token)).andExpect(status().isNotFound());
            mvc.perform(post("/h/{t}/send/{id}", token, fathima.getId())).andExpect(status().isNotFound());
        }
        assertThat(guests.findById(fathima.getId()).orElseThrow().isSent()).isFalse();
    }

    @Test
    void exportsCsvWithoutPhones() throws Exception {
        byte[] body = mvc.perform(get("/h/{t}/guests.csv", event.getHostToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("guests.csv")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFName,Sent,Sent at,RSVP,Headcount,Responded at\r\n");
        assertThat(csv).containsPattern("Anil,Yes,\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d,Attending,3,");
        assertThat(csv).contains("Fathima,No,,Not attending,,");
        assertThat(csv).contains("\"'=Bob, Jr.\",No,,Pending,,\r\n");
        assertThat(csv).doesNotContain("9876543210").doesNotContain("501234567");
    }

    @Test
    void sendScreenHighlightsFirstUnsentGuestAndHidesPhones() throws Exception {
        String html = mvc.perform(get("/h/{t}/send", event.getHostToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("id=\"guest-" + fathima.getId() + "\"[^>]*class=\"current\"");
        assertThat(html).containsOnlyOnce("class=\"current\"");
        assertThat(html).contains("action=\"/h/" + event.getHostToken() + "/send/" + bob.getId() + "\"");
        assertThat(html).doesNotContain("wa.me").doesNotContain("9876543210").doesNotContain("501234567");
    }

    @Test
    void sendMarksGuestAsSentAndRedirectsToWhatsApp() throws Exception {
        String location = mvc.perform(post("/h/{t}/send/{id}", event.getHostToken(), fathima.getId()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://wa.me/971501234567?text=");
        String text = URLDecoder.decode(location.substring(location.indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertThat(text)
                .startsWith("Dear Fathima,\n\nWe joyfully invite you")
                .contains("ഞങ്ങളുടെ വിവാഹത്തിലേക്ക്")
                .endsWith("https://invite.test/i/" + fathima.getToken());
        assertThat(guests.findById(fathima.getId()).orElseThrow().getSentAt()).isNotNull();
    }

    @Test
    void cannotSendToGuestOfAnotherEvent() throws Exception {
        var otherGuest = data.guest(data.event(), "Stranger", "919999999999");

        mvc.perform(post("/h/{t}/send/{id}", event.getHostToken(), otherGuest.getId()))
                .andExpect(status().isNotFound());
        assertThat(guests.findById(otherGuest.getId()).orElseThrow().isSent()).isFalse();
    }
}
