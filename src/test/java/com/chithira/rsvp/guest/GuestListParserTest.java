package com.chithira.rsvp.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.chithira.rsvp.guest.GuestListParser.ParsedGuest;
import org.junit.jupiter.api.Test;

class GuestListParserTest {

    @Test
    void parsesValidLinesAndSkipsBlankOnes() {
        var result = GuestListParser.parse("""
                Anil Kumar, 98765 43210

                  Fathima, +971 50 123 4567
                Dr. Thomas, Jr., 09876501234
                """);

        assertThat(result.hasErrors()).isFalse();
        assertThat(result.guests()).containsExactly(
                new ParsedGuest(1, "Anil Kumar", "919876543210"),
                new ParsedGuest(3, "Fathima", "971501234567"),
                new ParsedGuest(4, "Dr. Thomas, Jr.", "919876501234"));
    }

    @Test
    void reportsBadLinesWithLineNumbers() {
        var result = GuestListParser.parse("""
                Anil, 9876543210
                no comma here
                , 9876543211
                Bob, 12345
                Anil again, +91 98765 43210
                """);

        assertThat(result.guests()).hasSize(1);
        assertThat(result.errors())
                .extracting(GuestListParser.LineError::lineNumber)
                .containsExactly(2, 3, 4, 5);
        assertThat(result.errors().get(3).message()).contains("more than once");
    }

    @Test
    void handlesWindowsLineEndingsAndNull() {
        assertThat(GuestListParser.parse("A, 9876543210\r\nB, 9876543211\r\n").guests()).hasSize(2);
        assertThat(GuestListParser.parse(null).guests()).isEmpty();
    }
}
