package com.chithira.rsvp.invite;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits RSVP submissions ({@code POST /i/{token}/rsvp}) per client IP. The client IP is
 * {@code request.getRemoteAddr()}, which Tomcat's RemoteIpValve only rewrites from
 * X-Forwarded-For when the request came through a trusted internal proxy.
 */
public class RsvpRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern RSVP_PATH = Pattern.compile("^/i/[^/]+/rsvp/?$");

    private final SlidingWindowRateLimiter limiter;

    public RsvpRateLimitFilter(SlidingWindowRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !RSVP_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiter.hit(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write(
                "Too many replies from your connection. Please wait a minute and try again."
                        .getBytes(StandardCharsets.UTF_8));
    }
}
