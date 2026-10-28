package com.chithira.rsvp.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void tokensAre128BitUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = Tokens.newToken();
            assertThat(token).hasSize(22).matches("[A-Za-z0-9_-]+");
            assertThat(Tokens.looksValid(token)).isTrue();
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(Tokens.looksValid(null)).isFalse();
        assertThat(Tokens.looksValid("short")).isFalse();
        assertThat(Tokens.looksValid("aaaaaaaaaaaaaaaaaaaaa/")).isFalse();
    }
}
