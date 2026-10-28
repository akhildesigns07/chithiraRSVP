package com.chithira.rsvp.guest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "9876543210, 919876543210",
            "09876543210, 919876543210",
            "+91 98765 43210, 919876543210",
            "+91-98765-43210, 919876543210",
            "919876543210, 919876543210",
            "0091 9876543210, 919876543210",
            "(+91) 6123456789, 916123456789",
            "0501234567, 971501234567",
            "050 123 4567, 971501234567",
            "501234567, 971501234567",
            "+971 50 123 4567, 971501234567",
            "00971501234567, 971501234567",
            "971 55 987 6543, 971559876543",
    })
    void normalisesIndianAndUaeMobiles(String raw, String expected) {
        assertThat(PhoneNormalizer.normalize(raw)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "abc", "12345",
            "5876543210",          // Indian mobiles start with 6-9
            "98765432101",         // too long
            "+44 7700 900123",     // other country
            "+91 5876543210",      // invalid Indian mobile with country code
            "+971 4 123 4567",     // UAE landline
            "+9876543210",         // national number written with +
            "98765x43210",
    })
    void rejectsEverythingElse(String raw) {
        assertThat(PhoneNormalizer.normalize(raw)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "919876543210, +91 98765 43210",
            "971501234567, +971 50 123 4567",
    })
    void formatsForDisplay(String normalized, String expected) {
        assertThat(PhoneNormalizer.format(normalized)).isEqualTo(expected);
    }
}
