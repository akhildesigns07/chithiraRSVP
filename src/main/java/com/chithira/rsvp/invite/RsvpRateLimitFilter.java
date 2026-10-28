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
 * Limits RSVP submissions ({@code POST /i/{token}/rsvp}) per client IP.
 *
 * <p>The client IP is taken from {@code clientIpHeader} when configured (behind Cloudflare:
 * {@code CF-Connecting-IP}, which Cloudflare always overwrites), otherwise from
 * {@code request.getRemoteAddr()}. X-Forwarded-For is never used directly: its first entry is
 * whatever the client sent.
 */
public class RsvpRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern RSVP_PATH = Pattern.compile("^/i/[^/]+/rsvp/?$");

    private final SlidingWindowRateLimiter limiter;
    private final String clientIpHeader;

    public RsvpRateLimitFilter(SlidingWindowRateLimiter limiter, String clientIpHeader) {
        this.limiter = limiter;
        this.clientIpHeader = clientIpHeader == null || clientIpHeader.isBlank() ? null : clientIpHeader.strip();
    }

    String clientIp(HttpServletRequest request) {
        if (clientIpHeader != null) {
            String value = request.getHeader(clientIpHeader);
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !RSVP_PATH.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var decision = limiter.hit(clientIp(request));
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
