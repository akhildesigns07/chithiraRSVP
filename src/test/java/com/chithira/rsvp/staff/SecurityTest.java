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

import com.chithira.rsvp.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SecurityTest {

    @Autowired
    MockMvc mvc;

    @Test
    void staffPagesRequireLogin() throws Exception {
        mvc.perform(get("/portal")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/portal/login"));
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
