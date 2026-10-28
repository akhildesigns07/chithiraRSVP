package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    /** Clock the test can move forward. */
    static final class MutableClock extends Clock {
        long millis = 1_000_000;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10, Duration.ofMinutes(1), clock);

    @Test
    void allowsTenPerMinuteThenRejectsWithRetryAfter() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.hit("1.2.3.4").allowed()).isTrue();
            clock.millis += 1000;
        }
        var denied = limiter.hit("1.2.3.4");
        assertThat(denied.allowed()).isFalse();
        // The first hit was 10 s ago, so it leaves the window in 50 s.
        assertThat(denied.retryAfterSeconds()).isEqualTo(50);
    }

    @Test
    void windowSlidesRatherThanResettingOnTheMinute() {
        for (int i = 0; i < 10; i++) {        // hits at t = 0 s, 1 s, ... 9 s
            limiter.hit("ip");
            clock.millis += 1000;
        }
        clock.millis += 49_999;               // t = 59.999 s: nothing has expired yet
        assertThat(limiter.hit("ip").allowed()).isFalse();
        clock.millis += 1;                    // t = 60 s: only the t = 0 hit has expired
        assertThat(limiter.hit("ip").allowed()).isTrue();
        assertThat(limiter.hit("ip").allowed()).isFalse();
    }

    @Test
    void rejectedHitsDoNotExtendTheBan() {
        for (int i = 0; i < 10; i++) {
            limiter.hit("ip");
        }
        for (int i = 0; i < 50; i++) {
            limiter.hit("ip");
        }
        clock.millis += 60_000;
        assertThat(limiter.hit("ip").allowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 10; i++) {
            limiter.hit("a");
        }
        assertThat(limiter.hit("a").allowed()).isFalse();
        assertThat(limiter.hit("b").allowed()).isTrue();
    }

    @Test
    void sweepForgetsIdleClients() {
        limiter.hit("a");
        limiter.hit("b");
        clock.millis += 60_000;
        limiter.sweep(clock.millis());
        assertThat(limiter.trackedKeys()).isZero();
    }
}
