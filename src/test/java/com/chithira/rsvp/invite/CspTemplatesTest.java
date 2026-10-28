package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The CSP allows no inline or eval'd script, so no template may contain any. */
class CspTemplatesTest {

    @Test
    void templatesHaveNoInlineScriptOrEventHandlers() throws IOException {
        List<Path> templates;
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/templates"))) {
            templates = files.filter(p -> p.toString().endsWith(".html")).toList();
        }
        assertThat(templates).isNotEmpty();
        for (Path t : templates) {
            String html = Files.readString(t, StandardCharsets.UTF_8);
            assertThat(html).as(t.toString())
                    .doesNotContainPattern("\\son[a-z]+\\s*=")
                    .doesNotContainPattern("hx-on")
                    .doesNotContainPattern("<script(?![^>]*\\bsrc=)[^>]*>")
                    .doesNotContain("javascript:");
        }
    }
}
