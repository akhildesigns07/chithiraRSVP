package com.chithira.rsvp.config;

import com.chithira.rsvp.guest.PhoneNormalizer;
import jakarta.validation.constraints.NotBlank;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String baseUrl, Admin admin, Shop shop) {

    public record Admin(@NotBlank String username, @NotBlank String password) {
    }

    /** Print shop details shown on the public homepage. */
    public record Shop(String name, String address, String phone, String whatsapp, String email) {

        public String phoneHref() {
            return "tel:" + PhoneNormalizer.normalize(phone).map(p -> "+" + p).orElse(phone);
        }

        public String phoneDisplay() {
            return PhoneNormalizer.normalize(phone).map(PhoneNormalizer::format).orElse(phone);
        }

        public String whatsappHref() {
            String number = whatsapp == null || whatsapp.isBlank() ? phone : whatsapp;
            return "https://wa.me/" + PhoneNormalizer.normalize(number).orElse(number.replaceAll("\\D", ""));
        }

        public String mapsHref() {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(name + ", " + address, StandardCharsets.UTF_8);
        }
    }

    /** Base URL without a trailing slash. */
    public String publicBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
