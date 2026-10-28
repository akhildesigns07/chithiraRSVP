package com.chithira.rsvp.config;

import com.chithira.rsvp.invite.RsvpRateLimitFilter;
import com.chithira.rsvp.invite.SlidingWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RateLimitConfig {

    @Bean
    SlidingWindowRateLimiter rsvpRateLimiter(AppProperties props) {
        return new SlidingWindowRateLimiter(props.rateLimit().rsvpPerMinute(), Duration.ofMinutes(1), Clock.systemUTC());
    }

    /** Runs before Spring Security so rejected requests do no further work. */
    @Bean
    FilterRegistrationBean<RsvpRateLimitFilter> rsvpRateLimitFilter(SlidingWindowRateLimiter limiter, AppProperties props) {
        var registration = new FilterRegistrationBean<>(new RsvpRateLimitFilter(limiter, props.rateLimit().clientIpHeader()));
        registration.addUrlPatterns("/i/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
