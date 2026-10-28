package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.GuestRepository;
import com.chithira.rsvp.guest.RsvpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@TestPropertySource(properties = "app.rate-limit.rsvp-per-minute=10")
class RsvpRateLimitTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    GuestRepository guests;

    Guest guest;

    @BeforeEach
    void setUp() {
        guest = data.guest(data.event(), "Anil", "919876543210");
    }

    private static RequestPostProcessor from(String ip) {
        return req -> {
            req.setRemoteAddr(ip);
            return req;
        };
    }

    @Test
    void eleventhRsvpWithinAMinuteFromOneIpIsRejected() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.1"))
                            .param("attending", "yes").param("headcount", "2"))
                    .andExpect(status().is3xxRedirection());
        }

        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.1"))
                        .param("attending", "no"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        // The rejected request never reached the controller.
        assertThat(guests.findById(guest.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.ATTENDING);
    }

    @Test
    void limitIsPerIpAndOnlyForRsvpPosts() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.2")).param("attending", "no"));
        }
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.2")).param("attending", "no"))
                .andExpect(status().isTooManyRequests());

        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.3")).param("attending", "no"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/i/{t}", guest.getToken()).with(from("198.51.100.2")))
                .andExpect(status().isOk());
    }

    @Test
    void spoofedForwardedForHeaderDoesNotBypassTheLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.4"))
                    .header("X-Forwarded-For", "10.0.0." + i).param("attending", "no"));
        }
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.4"))
                        .header("X-Forwarded-For", "10.0.0.99").param("attending", "no"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void wrongTokensCountTowardsTheLimitToo() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/i/{t}/rsvp", "AAAAAAAAAAAAAAAAAAAAA" + i).with(from("198.51.100.5")).param("attending", "no"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/i/{t}/rsvp", guest.getToken()).with(from("198.51.100.5")).param("attending", "no"))
                .andExpect(status().isTooManyRequests());
    }
}
