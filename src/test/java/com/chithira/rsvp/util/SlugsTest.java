package com.chithira.rsvp.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Anjali & Rahul|anjali-rahul",
            "  José  &  Mária |jose-maria",
            "അഞ്ജലി & രാഹുൽ|event",
            "A very very very long couple name & another very long name|a-very-very-very-long-couple-name",
    })
    void readablePartIsAsciiAndShort(String names, String prefix) {
        String slug = Slugs.forEvent(names);
        assertThat(slug).matches(java.util.regex.Pattern.quote(prefix) + "-[a-z0-9]{12}");
        assertThat(Slugs.looksValid(slug)).isTrue();
    }

    @Test
    void suffixIsRandom() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(Slugs.forEvent("A & B"))).isTrue();
        }
    }
}
