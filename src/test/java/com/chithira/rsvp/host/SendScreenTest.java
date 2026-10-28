package com.chithira.rsvp.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestRepository;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SendScreenTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    GuestRepository guests;
    @Autowired
    EventRepository events;
    @Autowired
    EventMessageRepository messages;

    Event event;
    Guest anil;
    Guest fathima;
    Guest bob;

    @BeforeEach
    void setUp() {
        event = data.event();
        anil = data.guest(event, "Anil", "919876543210");
        fathima = data.guest(event, "Fathima", "971501234567");
        bob = data.guest(event, "Bob", "919876543211");
        anil.markSent();
        fathima.markSent();
        bob.attend(2);
        guests.flush();
    }

    private String sendPage() throws Exception {
        return mvc.perform(get("/h/{t}/send", event.getHostToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String sentText(String location) {
        return URLDecoder.decode(location.substring(location.indexOf("text=") + 5), StandardCharsets.UTF_8);
    }

    @Test
    void showsProgressFilterCountsAndRowState() throws Exception {
        String html = sendPage();
        assertThat(html).contains("<span id=\"sent-count\">2</span> of <span id=\"total-count\">3</span> sent");
        assertThat(html).containsPattern("data-count=\"all\">3<");
        assertThat(html).containsPattern("data-count=\"unsent\">1<");
        assertThat(html).containsPattern("data-count=\"sent\">2<");
        assertThat(html).containsPattern("data-count=\"rsvp\">1<");
        assertThat(html).containsPattern("data-id=\"" + bob.getId() + "\" data-name=\"Bob\" data-sent=\"false\" data-rsvp=\"ATTENDING\"");
        assertThat(html).contains("/h/" + event.getHostToken() + "/guests/" + anil.getId() + "/unsend");
        assertThat(html).doesNotContain("9876543210", "501234567", "wa.me");
    }

    @Test
    void composerStartsWithBothDefaultMessages() throws Exception {
        String html = sendPage();
        assertThat(html).contains("Dear {name},\n\nWe joyfully invite you to celebrate our wedding.\n\n{link}");
        assertThat(html).contains("പ്രിയപ്പെട്ട {name},");
        assertThat(html).containsPattern("name=\"language\" value=\"EN\" checked");
    }

    @Test
    void sendUsesTheMessageOnScreenAndSavesIt() throws Exception {
        String location = mvc.perform(post("/h/{t}/send/{id}", event.getHostToken(), bob.getId())
                        .param("language", "ML")
                        .param("templateEn", "Hello {name}: {link}")
                        .param("templateMl", "നമസ്കാരം {name}!\r\n{link}"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(location).startsWith("https://wa.me/919876543211?text=");
        assertThat(sentText(location)).isEqualTo("നമസ്കാരം Bob!\nhttps://invite.test/i/" + bob.getToken());
        assertThat(guests.findById(bob.getId()).orElseThrow().isSent()).isTrue();

        var saved = messages.findById(event.getId()).orElseThrow();
        assertThat(saved.getLanguage()).isEqualTo(MessageLanguage.ML);
        assertThat(saved.getTemplateEn()).isEqualTo("Hello {name}: {link}");
        assertThat(sendPage()).contains("Hello {name}: {link}").containsPattern("name=\"language\" value=\"ML\" checked");
    }

    @Test
    void messageWithoutTheLinkIsRefusedAndNothingChanges() throws Exception {
        mvc.perform(post("/h/{t}/send/{id}", event.getHostToken(), bob.getId())
                        .param("language", "EN").param("templateEn", "Hi {name}").param("templateMl", "x {link}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("{link}")));

        assertThat(guests.findById(bob.getId()).orElseThrow().isSent()).isFalse();
        assertThat(messages.findById(event.getId())).isEmpty();
    }

    @Test
    void composerCanBeSavedWithoutSendingAndReset() throws Exception {
        mvc.perform(post("/h/{t}/composer", event.getHostToken())
                        .param("language", "EN").param("templateEn", "Custom {link}").param("templateMl", "x {link}"))
                .andExpect(content().string(containsString("Saved")));
        mvc.perform(post("/h/{t}/composer", event.getHostToken())
                        .param("language", "ML").param("templateEn", "Custom {link}").param("templateMl", "no link"))
                .andExpect(content().string(containsString("must include {link}")));
        assertThat(messages.findById(event.getId()).orElseThrow().getTemplateEn()).isEqualTo("Custom {link}");

        mvc.perform(post("/h/{t}/composer/reset", event.getHostToken())).andExpect(status().is3xxRedirection());
        assertThat(messages.findById(event.getId())).isEmpty();
    }

    @Test
    void untouchedDefaultsFollowLaterEditsToTheEventMessage() throws Exception {
        String defaultEn = "Dear {name},\n\nWe joyfully invite you to celebrate our wedding.\n\n{link}";
        mvc.perform(post("/h/{t}/composer", event.getHostToken())
                .param("language", "EN").param("templateEn", defaultEn).param("templateMl", "x {link}"));
        assertThat(messages.findById(event.getId()).orElseThrow().getTemplateEn()).isNull();

        event.setMessageEn("Updated invitation text.");
        events.saveAndFlush(event);
        assertThat(sendPage()).contains("Dear {name},\n\nUpdated invitation text.\n\n{link}");
    }

    @Test
    void undoMarksTheGuestAsNotSent() throws Exception {
        mvc.perform(post("/h/{t}/guests/{id}/unsend", event.getHostToken(), anil.getId()))
                .andExpect(status().isNoContent());
        assertThat(guests.findById(anil.getId()).orElseThrow().getSentAt()).isNull();
        assertThat(sendPage()).contains("<span id=\"sent-count\">1</span> of");
    }

    @Test
    void undoIsScopedToTheHostsOwnEventAndToken() throws Exception {
        var other = data.event();
        var stranger = data.guest(other, "Stranger", "919999999999");
        stranger.markSent();
        guests.flush();

        mvc.perform(post("/h/{t}/guests/{id}/unsend", event.getHostToken(), stranger.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/h/{t}/guests/{id}/unsend", "AAAAAAAAAAAAAAAAAAAAAA", anil.getId()))
                .andExpect(status().isNotFound());
        assertThat(guests.findById(stranger.getId()).orElseThrow().isSent()).isTrue();
        assertThat(guests.findById(anil.getId()).orElseThrow().isSent()).isTrue();
    }
}
