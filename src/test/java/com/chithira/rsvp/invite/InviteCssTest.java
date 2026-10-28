package com.chithira.rsvp.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Guards the animation rules for the card: CSS only, finite, done within 4 s, reduced motion respected. */
class InviteCssTest {

    private static String css() throws IOException {
        return new ClassPathResource("static/css/invite.css").getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static double seconds(String var, String css) {
        Matcher m = Pattern.compile("--" + var + ":\\s*([0-9.]+)s").matcher(css);
        assertThat(m.find()).as(var).isTrue();
        return Double.parseDouble(m.group(1));
    }

    @Test
    void entranceSequenceIsOrderedAndEndsWithinFourSeconds() throws IOException {
        String css = css();
        double greeting = seconds("at-greeting", css);
        double names = seconds("at-names", css);
        double details = seconds("at-details", css);
        double rsvp = seconds("at-rsvp", css);
        double stage = seconds("d-stage", css);

        assertThat(greeting).isLessThan(names);
        assertThat(names).isLessThan(details);
        assertThat(details).isLessThan(rsvp);
        assertThat(rsvp + stage).isLessThan(4.0);
        // The border has finished drawing (last edge: 0.75 s + 0.3 s) before the greeting appears.
        assertThat(0.75 + 0.3).isLessThanOrEqualTo(greeting + 0.2);
    }

    @Test
    void everyAnimationIsFiniteAndEndsWithinFourSeconds() throws IOException {
        String css = css();
        assertThat(css).doesNotContain("infinite");

        List<String> declarations = new ArrayList<>();
        Matcher m = Pattern.compile("animation:\\s*([^;}]+)").matcher(css);
        while (m.find()) {
            declarations.add(m.group(1).trim());
        }
        assertThat(declarations).isNotEmpty();
        for (String decl : declarations) {
            if (decl.startsWith("none")) {
                continue;
            }
            List<Double> times = new ArrayList<>();
            int iterations = 1;
            for (String token : decl.split("\\s+")) {
                if (token.matches("[0-9.]+s")) {
                    times.add(Double.parseDouble(token.substring(0, token.length() - 1)));
                } else if (token.matches("\\d+")) {
                    iterations = Integer.parseInt(token);
                }
            }
            double duration = times.isEmpty() ? 0 : times.get(0);
            double delay = times.size() > 1 ? times.get(1) : 0;
            assertThat(delay + duration * iterations).as(decl).isLessThan(4.0);
        }
        // Edge delays are set separately from the shorthand.
        Matcher delays = Pattern.compile("animation-delay:\\s*([0-9.]+)s").matcher(css);
        while (delays.find()) {
            assertThat(Double.parseDouble(delays.group(1)) + 0.3).isLessThan(4.0);
        }
    }

    @Test
    void reducedMotionTurnsEveryCardAnimationOff() throws IOException {
        String css = css();
        Matcher m = Pattern.compile("@media \\(prefers-reduced-motion: reduce\\)\\s*\\{(.*?)\\}\\s*\\}", Pattern.DOTALL).matcher(css);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).contains(".card *").contains("animation: none !important");
    }

    @Test
    void contentIsOnlyHiddenWhileWaitingForItsTurn() throws IOException {
        // fill-mode "backwards" (not "both"/"forwards") means nothing is left invisible if animations never run.
        String css = css();
        assertThat(css).doesNotContainPattern("animation:[^;]*\\b(both|forwards)\\b");
        assertThat(css).doesNotContainPattern("\\.stage\\s*\\{[^}]*opacity:\\s*0");
    }
}
