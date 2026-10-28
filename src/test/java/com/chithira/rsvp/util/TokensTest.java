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
    void hashIsStableHexSha256() {
        String token = Tokens.newToken();
        assertThat(Tokens.hash(token)).hasSize(64).matches("[0-9a-f]+").isEqualTo(Tokens.hash(token));
        assertThat(Tokens.hash(token)).isNotEqualTo(Tokens.hash(Tokens.newToken()));
        // Known vector: SHA-256("abc")
        assertThat(Tokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesComparesWholeValue() {
        String token = Tokens.newToken();
        assertThat(Tokens.matches(token, token)).isTrue();
        assertThat(Tokens.matches(token, token.substring(0, 21) + (token.charAt(21) == 'A' ? 'B' : 'A'))).isFalse();
        assertThat(Tokens.matches(token, token + "x")).isFalse();
        assertThat(Tokens.matches(token, null)).isFalse();
        assertThat(Tokens.matches(null, token)).isFalse();
    }

    @Test
    void rejectsMalformedTokens() {
        assertThat(Tokens.looksValid(null)).isFalse();
        assertThat(Tokens.looksValid("short")).isFalse();
        assertThat(Tokens.looksValid("aaaaaaaaaaaaaaaaaaaaa/")).isFalse();
    }
}
