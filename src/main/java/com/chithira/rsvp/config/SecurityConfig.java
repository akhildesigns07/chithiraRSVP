package com.chithira.rsvp.config;

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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(TOKEN_PAGES).permitAll()
                        .requestMatchers("/", "/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.ico", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/portal/login")
                        .defaultSuccessUrl("/portal", true)
                        .permitAll())
                .logout(logout -> logout.logoutUrl("/portal/logout").logoutSuccessUrl("/portal/login?logout"))
                // Token pages carry no session-based authority, so there is nothing for CSRF to protect there.
                .csrf(csrf -> csrf.ignoringRequestMatchers(TOKEN_PAGES))
                // Keep invite/host tokens out of Referer headers when guests follow the Maps link.
                .headers(headers -> headers
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        // The letter page embeds the PDF from the same origin.
                        .frameOptions(frame -> frame.sameOrigin()));
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
