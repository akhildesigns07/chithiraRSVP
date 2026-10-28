package com.chithira.rsvp.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AppPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AppProperties props(String user, String password) {
        return new AppProperties("https://x", new AppProperties.Admin(user, password), null, new AppProperties.RateLimit(10, null));
    }

    @Test
    void blankAdminCredentialsAreRejectedAtStartup() {
        assertThat(validator.validate(props("admin", ""))).isNotEmpty();
        assertThat(validator.validate(props("admin", "   "))).isNotEmpty();
        assertThat(validator.validate(props("", "secret"))).isNotEmpty();
        assertThat(validator.validate(props("admin", "secret"))).isEmpty();
    }

    @Test
    void rateLimitMustBePositive() {
        var p = new AppProperties("https://x", new AppProperties.Admin("a", "b"), null, new AppProperties.RateLimit(0, null));
        assertThat(validator.validate(p)).isNotEmpty();
    }
}
