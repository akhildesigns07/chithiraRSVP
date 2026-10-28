package com.chithira.rsvp.prod;

import static org.assertj.core.api.Assertions.assertThat;

import com.chithira.rsvp.TestData;
import com.chithira.rsvp.event.EventPhoto;
import com.chithira.rsvp.event.EventPhotoRepository;
import com.chithira.rsvp.guest.Guest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * The prod profile on a real embedded server, as Cloudflare would reach it. "test" only supplies the
 * in-memory database and credentials that prod reads from the environment; every other setting is prod's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.rate-limit.rsvp-per-minute=10",
        // A real server commits its data, so it gets its own database rather than the shared test one.
        "spring.datasource.url=jdbc:h2:mem:prodserver;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"})
@ActiveProfiles({"prod", "test"})
class ProdProfileServerTest {

    @LocalServerPort
    int port;
    @Autowired
    TestData data;
    @Autowired
    EventPhotoRepository photos;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private Guest guest;

    @BeforeEach
    void setUp() {
        var event = data.event();
        photos.save(new EventPhoto(event.getId(), "image/png", TestData.PNG));
        guest = data.guest(event, "Anil", "919876543210");
    }

    /** A request as Cloudflare forwards it: HTTPS at the edge, real client IP in CF-Connecting-IP. */
    private HttpResponse<String> viaCloudflare(String method, String path, String clientIp) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "6.6.6.6")
                .header("CF-Connecting-IP", clientIp);
        builder = "POST".equals(method)
                ? builder.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("attending=no"))
                : builder.GET();
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return viaCloudflare("GET", path, "198.51.100.10");
    }

    @Test
    void healthIsPublicAndRevealsNoDetails() throws Exception {
        var res = get("/actuator/health");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/info", "/actuator/env", "/actuator/beans", "/actuator/metrics",
            "/actuator/health/db", "/actuator/health/liveness", "/actuator/loggers", "/actuator/heapdump"})
    void nothingElseUnderActuatorIsReachable(String path) throws Exception {
        assertThat(get(path).statusCode()).isNotEqualTo(200);
    }

    @Test
    void securityHeadersOnHttpsRequests() throws Exception {
        var res = get("/i/" + guest.getToken());
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Strict-Transport-Security")).hasValue("max-age=31536000");
        assertThat(res.headers().firstValue("Content-Security-Policy")).get().asString()
                .contains("default-src 'self'", "script-src 'self'", "frame-ancestors 'self'", "form-action 'self' https://wa.me")
                .doesNotContain("unsafe-eval");
        assertThat(res.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(res.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
    }

    @Test
    void sessionCookieIsSecureHostOnlyAndHttpOnly() throws Exception {
        var res = get("/portal/login");
        String cookie = res.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("__Host-rsvp-session=")).findFirst().orElseThrow();
        assertThat(cookie).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain=");
    }

    @Test
    void redirectsStayOnHttpsBehindTheProxy() throws Exception {
        var res = get("/portal");
        assertThat(res.statusCode()).isEqualTo(302);
        assertThat(res.headers().firstValue("Location")).get().asString().startsWith("https://");
    }

    @Test
    void personalPagesAreNeverCached() throws Exception {
        for (String path : List.of("/i/" + guest.getToken(), "/i/" + guest.getToken() + "/photo",
                "/h/" + guest.getEvent().getHostToken(), "/h/" + guest.getEvent().getHostToken() + "/send")) {
            var res = get(path);
            assertThat(res.statusCode()).as(path).isEqualTo(200);
            assertThat(res.headers().firstValue("Cache-Control")).as(path).get().asString().contains("no-store");
        }
    }

    @Test
    void shareImagesAndStaticAssetsAreCachedForSevenDays() throws Exception {
        String page = get("/i/" + guest.getToken()).body();
        Matcher css = Pattern.compile("href=\"(/css/invite-[0-9a-f]{32}\\.css)\"").matcher(page);
        assertThat(css.find()).as("content-hashed stylesheet URL").isTrue();
        Matcher js = Pattern.compile("src=\"(/js/rsvp-[0-9a-f]{32}\\.js)\"").matcher(page);
        assertThat(js.find()).as("content-hashed script URL").isTrue();

        for (String path : List.of(css.group(1), js.group(1), "/css/app.css",
                "/webjars/htmx.org/2.0.4/dist/htmx.min.js", "/og/" + guest.getEvent().getOgSlug() + ".jpg")) {
            var res = get(path);
            assertThat(res.statusCode()).as(path).isEqualTo(200);
            assertThat(res.headers().firstValue("Cache-Control")).as(path).hasValue("max-age=604800, public");
        }
    }

    @Test
    void rateLimitKeysOnCloudflaresClientIpNotXForwardedFor() throws Exception {
        String rsvp = "/i/" + guest.getToken() + "/rsvp";
        for (int i = 0; i < 10; i++) {
            assertThat(viaCloudflare("POST", rsvp, "203.0.113.50").statusCode()).isEqualTo(302);
        }
        // Same Cloudflare client, even with a different X-Forwarded-For: limited.
        assertThat(viaCloudflare("POST", rsvp, "203.0.113.50").statusCode()).isEqualTo(429);
        // Another client behind Cloudflare is unaffected, though X-Forwarded-For is identical.
        assertThat(viaCloudflare("POST", rsvp, "203.0.113.51").statusCode()).isEqualTo(302);
    }
}
