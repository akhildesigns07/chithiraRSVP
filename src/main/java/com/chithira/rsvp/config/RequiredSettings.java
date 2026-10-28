package com.chithira.rsvp.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Stops startup when a required setting is missing. Spring Boot otherwise keeps an unresolvable
 * placeholder as literal text, so an unset ADMIN_PASSWORD would silently become the password
 * "${ADMIN_PASSWORD}". Runs before the application context, so nothing starts half-configured.
 */
public class RequiredSettings implements EnvironmentPostProcessor {

    /** Setting -> the environment variable that normally provides it. */
    static final Map<String, String> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("spring.datasource.url", "DB_URL");
        REQUIRED.put("spring.datasource.username", "DB_USERNAME");
        REQUIRED.put("app.base-url", "APP_BASE_URL");
        REQUIRED.put("app.admin.username", "ADMIN_USERNAME");
        REQUIRED.put("app.admin.password", "ADMIN_PASSWORD");
    }

    /** May be empty (e.g. an embedded test database), but must not be an unresolved placeholder. */
    static final Map<String, String> RESOLVABLE = Map.of("spring.datasource.password", "DB_PASSWORD");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((key, env) -> {
            String value = resolve(environment, key);
            if (value == null || value.isBlank()) {
                missing.add(env + " (" + key + ")");
            }
        });
        RESOLVABLE.forEach((key, env) -> {
            if (environment.containsProperty(key) && resolve(environment, key) == null) {
                missing.add(env + " (" + key + ")");
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required settings; set these environment variables: "
                    + String.join(", ", missing));
        }
    }

    /** The resolved value, or null if absent or it still refers to an undefined variable. */
    private static String resolve(ConfigurableEnvironment environment, String key) {
        try {
            String value = environment.getProperty(key);
            return value == null || value.contains("${") ? null : value;
        } catch (IllegalArgumentException unresolvable) {
            return null;
        }
    }
}
