package com.chithira.rsvp.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/**
 * One JSON object per line, with ECS-style field names, and every free-text field passed through
 * {@link LogRedactor}. Enabled in production via {@code logging.structured.format.console}.
 */
public class RedactingJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String serviceName;

    public RedactingJsonFormatter(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", "chithira-rsvp");
    }

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        out.put("log.level", event.getLevel().toString());
        out.put("log.logger", event.getLoggerName());
        out.put("process.thread.name", event.getThreadName());
        out.put("service.name", serviceName);
        out.put("message", LogRedactor.redact(event.getFormattedMessage()));
        IThrowableProxy error = event.getThrowableProxy();
        if (error != null) {
            out.put("error.type", error.getClassName());
            out.put("error.message", LogRedactor.redact(error.getMessage()));
            out.put("error.stack_trace", LogRedactor.redact(ThrowableProxyUtil.asString(error)));
        }
        try {
            return JSON.writeValueAsString(out) + "\n";
        } catch (JsonProcessingException e) {
            return "{\"log.level\":\"ERROR\",\"message\":\"log formatting failed\"}\n";
        }
    }
}
