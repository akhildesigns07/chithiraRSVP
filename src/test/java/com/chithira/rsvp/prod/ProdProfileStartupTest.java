package com.chithira.rsvp.prod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chithira.rsvp.RsvpApplication;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;

class ProdProfileStartupTest {

    private static SpringApplicationBuilder prodApp(String... properties) {
        return new SpringApplicationBuilder(RsvpApplication.class)
                .profiles("prod")
                .properties("server.port=0")
                .properties(properties);
    }

    @Test
    void refusesToStartAndNamesEveryMissingVariable() {
        assertThatThrownBy(() -> prodApp().run().close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD")
                .hasMessageContaining("APP_BASE_URL")
                .hasMessageContaining("ADMIN_USERNAME")
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void anUnsetPasswordNeverBecomesTheLiteralPlaceholder() {
        // Everything else provided: only the admin password is missing, or blank.
        String[] allButPassword = {"DB_URL=jdbc:h2:mem:x", "DB_USERNAME=sa", "DB_PASSWORD=", "APP_BASE_URL=https://x", "ADMIN_USERNAME=admin"};
        assertThatThrownBy(() -> prodApp(allButPassword).run().close())
                .hasMessageContaining("ADMIN_PASSWORD (app.admin.password)")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("DB_URL"));
        String[] blank = {"DB_URL=jdbc:h2:mem:x", "DB_USERNAME=sa", "DB_PASSWORD=", "APP_BASE_URL=https://x", "ADMIN_USERNAME=admin", "ADMIN_PASSWORD="};
        assertThatThrownBy(() -> prodApp(blank).run().close())
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void prodProfileHasNoDefaultsForSecrets() throws Exception {
        String yml = new ClassPathResource("application-prod.yml").getContentAsString(StandardCharsets.UTF_8);
        for (String secret : new String[] {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "APP_BASE_URL", "ADMIN_USERNAME", "ADMIN_PASSWORD"}) {
            assertThat(yml).contains("${" + secret + "}");
        }
        assertThat(yml).contains("forward-headers-strategy: framework", "secure: true");
    }
}
