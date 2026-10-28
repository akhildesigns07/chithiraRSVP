package com.chithira.rsvp.invite;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter: at most {@code limit} hits per key within any {@code window}.
 * Per-key updates are atomic ({@link ConcurrentHashMap#compute}); idle keys are evicted so memory
 * stays proportional to recently active clients. State is per instance, so a multi-instance
 * deployment gets one budget per instance.
 */
public class SlidingWindowRateLimiter {

    /** Sweep idle keys at most this often. */
    private static final int SWEEP_EVERY_N_CALLS = 1000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private int calls;

    public SlidingWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** Result of a hit: whether it is allowed, and if not, seconds until the next one would be. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision hit(String key) {
        long now = clock.millis();
        Decision[] result = new Decision[1];
        hits.compute(key, (k, times) -> {
            Deque<Long> t = times == null ? new ArrayDeque<>() : times;
            evictExpired(t, now);
            if (t.size() >= limit) {
                long retryMillis = t.peekFirst() + windowMillis - now;
                result[0] = new Decision(false, Math.max(1, (retryMillis + 999) / 1000));
            } else {
                t.addLast(now);
                result[0] = new Decision(true, 0);
            }
            return t;
        });
        if (++calls % SWEEP_EVERY_N_CALLS == 0) {
            sweep(now);
        }
        return result[0];
    }

    int trackedKeys() {
        return hits.size();
    }

    void sweep(long now) {
        for (String key : hits.keySet()) {
            hits.computeIfPresent(key, (k, t) -> {
                evictExpired(t, now);
                return t.isEmpty() ? null : t;
            });
        }
    }

    private void evictExpired(Deque<Long> times, long now) {
        while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) {
            times.pollFirst();
        }
    }
}
