package com.chithira.rsvp.staff;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.chithira.rsvp.IntegrationTest;
import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.EventRepository;
import com.chithira.rsvp.guest.GuestRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SecurityTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData data;
    @Autowired
    EventRepository events;
    @Autowired
    GuestRepository guests;

    @ParameterizedTest
    @ValueSource(strings = {
            "/portal", "/portal/events/new", "/portal/events/1", "/portal/events/1/edit",
            "/portal/events/1/photo", "/portal/events/1/letter/1", "/anything-else"})
    void staffPagesRequireLogin(String url) throws Exception {
        mvc.perform(get(url))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/portal/login"))
                .andExpect(unauthenticated());
    }

    @Test
    void staffPostsRequireLoginEvenWithCsrfToken() throws Exception {
        var event = data.event();

        mvc.perform(post("/portal/events/{id}/guests", event.getId()).param("guests", "A, 9876543210").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/portal/login"));
        mvc.perform(multipart("/portal/events").param("coupleNames", "X").with(csrf()))
                .andExpect(redirectedUrlPattern("**/portal/login"));

        assertThat(guests.findByEventIdOrderById(event.getId())).isEmpty();
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffFormsRejectMissingOrBadCsrfToken() throws Exception {
        var event = data.event();

        mvc.perform(post("/portal/events/{id}/guests", event.getId()).param("guests", "A, 9876543210"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/portal/events/{id}/guests", event.getId()).param("guests", "A, 9876543210")
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/portal/events/{id}", event.getId()).param("coupleNames", "Changed"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/portal/logout")).andExpect(status().isForbidden());

        assertThat(guests.findByEventIdOrderById(event.getId())).isEmpty();
        assertThat(events.findById(event.getId()).orElseThrow().getCoupleNames()).isEqualTo("Anjali & Rahul");
    }

    @Test
    void loginFormItselfRequiresCsrfToken() throws Exception {
        mvc.perform(post("/portal/login").param("username", "admin").param("password", "secret"))
                .andExpect(status().isForbidden())
                .andExpect(unauthenticated());
    }

    @Test
    void staffPagesRenderTheCsrfTokenIntoForms() throws Exception {
        mvc.perform(get("/portal/login")).andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void homepageIsPublicAndLinksToPortal() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Chithira Printer"),
                        containsString("TD Complex, Chamakkada, Kollam"),
                        containsString("tel:+919995360201"),
                        containsString("+91 99953 60201"),
                        containsString("https://wa.me/919995360201"),
                        containsString("mailto:chithiraprinterskollam@gmail.com"),
                        containsString("href=\"/portal\""))));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/portal/login")).andExpect(status().isOk());
    }

    @Test
    void adminFromConfigCanLogIn() throws Exception {
        mvc.perform(formLogin("/portal/login").user("admin").password("secret"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/portal"));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(formLogin("/portal/login").user("admin").password("nope")).andExpect(unauthenticated());
    }
}
