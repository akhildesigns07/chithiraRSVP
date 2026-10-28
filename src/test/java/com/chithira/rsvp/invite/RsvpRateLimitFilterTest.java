package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RsvpRateLimitFilterTest {

    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10, Duration.ofMinutes(1), Clock.systemUTC());

    private static MockHttpServletRequest request() {
        var req = new MockHttpServletRequest("POST", "/i/t/rsvp");
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "6.6.6.6");
        return req;
    }

    @Test
    void usesTheTrustedHeaderWhenConfiguredAndPresent() {
        var filter = new RsvpRateLimitFilter(limiter, "CF-Connecting-IP");
        var req = request();
        req.addHeader("CF-Connecting-IP", " 203.0.113.7 ");
        assertThat(filter.clientIp(req)).isEqualTo("203.0.113.7");
    }

    @Test
    void fallsBackToTheRemoteAddressNeverToXForwardedFor() {
        assertThat(new RsvpRateLimitFilter(limiter, "CF-Connecting-IP").clientIp(request())).isEqualTo("10.0.0.1");
        assertThat(new RsvpRateLimitFilter(limiter, "").clientIp(request())).isEqualTo("10.0.0.1");
        assertThat(new RsvpRateLimitFilter(limiter, null).clientIp(request())).isEqualTo("10.0.0.1");
    }
}
