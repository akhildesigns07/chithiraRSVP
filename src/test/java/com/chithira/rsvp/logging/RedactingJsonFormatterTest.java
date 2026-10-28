package com.chithira.rsvp.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.chithira.rsvp.util.Tokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class RedactingJsonFormatterTest {

    private final RedactingJsonFormatter formatter =
            new RedactingJsonFormatter(new MockEnvironment().withProperty("spring.application.name", "chithira-rsvp"));
    private final LoggerContext context = new LoggerContext();

    private JsonNode format(Level level, String message, Throwable error, Object... args) throws Exception {
        var event = new LoggingEvent("fqcn", context.getLogger("com.example.Thing"), level, message, error, args);
        String line = formatter.format(event);
        assertThat(line).endsWith("\n").doesNotContain("\n{");
        return new ObjectMapper().readTree(line);
    }

    @Test
    void writesOneEcsStyleJsonObjectPerLine() throws Exception {
        JsonNode json = format(Level.INFO, "Started in {} seconds", null, 3.2);
        assertThat(json.get("log.level").asText()).isEqualTo("INFO");
        assertThat(json.get("log.logger").asText()).isEqualTo("com.example.Thing");
        assertThat(json.get("service.name").asText()).isEqualTo("chithira-rsvp");
        assertThat(json.get("message").asText()).isEqualTo("Started in 3.2 seconds");
        assertThat(json.get("@timestamp").asText()).matches("\\d{4}-\\d\\d-\\d\\dT.*Z");
        assertThat(json.has("error.type")).isFalse();
    }

    @Test
    void redactsPhonesTokensAndHashesEverywhereIncludingExceptions() throws Exception {
        String token = Tokens.newToken();
        String hash = Tokens.hash(token);
        var error = new IllegalStateException(
                "duplicate key value violates unique constraint: Key (event_id, phone)=(1, 919876543210) for " + token);

        JsonNode json = format(Level.ERROR, "GET /i/{} failed for +91 98765 43210 / 050-123-4567 hash {}", error, token, hash);

        String all = json.toString();
        assertThat(all).doesNotContain(token, hash, "919876543210", "98765 43210", "050-123-4567");
        assertThat(json.get("message").asText()).isEqualTo("GET /i/[token] failed for [phone] / [phone] hash [hash]");
        assertThat(json.get("error.type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(json.get("error.message").asText()).contains("(1, [phone]) for [token]");
        assertThat(json.get("error.stack_trace").asText()).contains("[phone]").contains("at com.chithira");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Tomcat started on port 8080 (http) with context path '/'|Tomcat started on port 8080 (http) with context path '/'",
            "Started RsvpApplication in 4.123 seconds (process running for 4.5)|Started RsvpApplication in 4.123 seconds (process running for 4.5)",
            "Migrating schema \"public\" to version \"5 - Og images and messages\"|Migrating schema \"public\" to version \"5 - Og images and messages\"",
            "HikariPool-1 - Start completed. 2026-10-28T12:00:00|HikariPool-1 - Start completed. 2026-10-28T12:00:00",
            "UAE 971501234567 and IN 9876543210|UAE [phone] and IN [phone]",
    })
    void leavesOrdinaryLogTextAlone(String in, String out) {
        assertThat(LogRedactor.redact(in)).isEqualTo(out);
    }
}
