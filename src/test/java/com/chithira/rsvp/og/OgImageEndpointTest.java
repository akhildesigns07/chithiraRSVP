package com.chithira.rsvp.og;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventPhoto;
import com.chithira.rsvp.event.EventPhotoRepository;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.guest.Guest;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OgImageEndpointTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    EventRepository events;
    @Autowired
    EventPhotoRepository photos;
    @MockitoSpyBean
    OgImageRenderer renderer;

    Event event;
    Guest guest;

    @BeforeEach
    void setUp() throws Exception {
        event = data.event();
        photos.save(new EventPhoto(event.getId(), "image/jpeg", OgImageRendererTest.photo(600, 800, "jpeg")));
        guest = data.guest(event, "Anil Kumar", "919876543210");
        clearInvocations(renderer);
    }

    private String page() throws Exception {
        return mvc.perform(get("/i/{t}", guest.getToken())).andReturn().getResponse().getContentAsString();
    }

    @Test
    void invitePageHasOpenGraphTags() throws Exception {
        String html = page();
        assertThat(html).contains("<meta property=\"og:title\" content=\"You&#39;re invited: Anjali &amp; Rahul\">");
        assertThat(html).contains("<meta property=\"og:description\" content=\"Sunday, 14 February 2027 · 10:30 AM · St. Mary&#39;s Church, Kottayam\">");
        assertThat(html).containsPattern("<meta property=\"og:image\" content=\"https://invite\\.test/og/anjali-rahul-[a-z0-9]{12}\\.jpg\\?v=\\d+\">");
        assertThat(html).contains("og:image:width\" content=\"1200\"", "og:image:height\" content=\"630\"");
        // The preview is event-level: no guest name or token in the OG tags.
        String og = html.substring(html.indexOf("og:type"), html.indexOf("twitter:card"));
        assertThat(og).doesNotContain("Anil", guest.getToken(), "9876543210");
    }

    @Test
    void imageIsPublicJpegAndRenderedOnceThenServedFromCache() throws Exception {
        String url = "/og/" + event.getOgSlug() + ".jpg";
        byte[] first = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andReturn().getResponse().getContentAsByteArray();
        var img = ImageIO.read(new ByteArrayInputStream(first));
        assertThat(img.getWidth()).isEqualTo(1200);
        assertThat(img.getHeight()).isEqualTo(630);

        byte[] second = mvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray();
        assertThat(second).isEqualTo(first);
        verify(renderer, times(1)).render(any(), any());
    }

    @Test
    void changingTheEventRedrawsTheImageAndChangesItsUrl() throws Exception {
        String url = "/og/" + event.getOgSlug() + ".jpg";
        String before = page();
        byte[] first = mvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray();

        Thread.sleep(5);
        event.setCoupleNames("Anjali & Rahul Menon");
        event.touch();
        events.saveAndFlush(event);

        byte[] second = mvc.perform(get(url)).andReturn().getResponse().getContentAsByteArray();
        assertThat(second).isNotEqualTo(first);
        verify(renderer, times(2)).render(any(), any());
        assertThat(page()).isNotEqualTo(before);
        // The slug stays put; only the ?v= version moves, so WhatsApp refetches.
        assertThat(page()).contains("/og/" + event.getOgSlug() + ".jpg?v=" + OgImageService.version(event));
    }

    @Test
    void unknownOrMalformedSlugsAre404() throws Exception {
        mvc.perform(get("/og/{s}.jpg", "anjali-rahul-000000000000")).andExpect(status().isNotFound());
        mvc.perform(get("/og/{s}.jpg", "x")).andExpect(status().isNotFound());
        mvc.perform(get("/og/{s}.jpg", "UPPER-case-slug-xx")).andExpect(status().isNotFound());
        verify(renderer, times(0)).render(any(), any());
    }

    @Test
    void slugsAreReadableButUnguessable() {
        var other = data.event();
        assertThat(event.getOgSlug()).matches("anjali-rahul-[a-z0-9]{12}");
        assertThat(other.getOgSlug()).isNotEqualTo(event.getOgSlug());
    }
}
