package com.chithira.rsvp.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    /** Pages reachable by guests and hosts; the random token in the URL is the credential. */
    private static final String[] TOKEN_PAGES = {"/i/**", "/h/**"};

    /**
     * Same-origin everything, plus Google Fonts. No inline or eval'd script anywhere (htmx hx-on is
     * not used for that reason). Inline styles stay allowed for the few style attributes the
     * templates set (progress widths). form-action allows the WhatsApp hand-off: the Send form posts
     * here and is redirected to wa.me, which redirects to api.whatsapp.com.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'self'",
            "frame-src 'self'",
            "frame-ancestors 'self'",
            "base-uri 'self'",
            "form-action 'self' https://wa.me https://api.whatsapp.com");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(TOKEN_PAGES).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/", "/og/**", "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/portal/login")
                        .defaultSuccessUrl("/portal", true)
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/portal/logout").logoutSuccessUrl("/portal/login?logout"))
                // Token pages carry no session-based authority, so there is nothing for CSRF to protect there.
                .csrf(csrf -> csrf.ignoringRequestMatchers(TOKEN_PAGES))
                .headers(headers -> headers
                        // Keep invite/host tokens out of Referer headers when guests follow the Maps link.
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        // The letter page embeds the PDF from the same origin.
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        // Sent on HTTPS requests only. Subdomains are left out: the shop's domain may host other sites.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds())
                                .includeSubDomains(false)));
        // Cache-Control: pages and other responses that don't set their own get Spring Security's
        // "no-cache, no-store, max-age=0, must-revalidate" (all /i/** pages). Static resources and
        // /og images set their own 7-day public caching, which Spring Security leaves alone.
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppProperties props, PasswordEncoder encoder) {
        var admin = User.withUsername(props.admin().username())
                .password(encoder.encode(props.admin().password()))
                .roles("STAFF")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
