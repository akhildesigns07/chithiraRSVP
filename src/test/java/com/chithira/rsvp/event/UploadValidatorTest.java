package com.chithira.rsvp.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chithira.rsvp.TestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class UploadValidatorTest {

    private static MockMultipartFile file(String name, byte[] data) {
        return new MockMultipartFile("letter", name, "application/octet-stream", data);
    }

    @Test
    void emptyLetterUploadMeansNoChange() {
        assertThat(UploadValidator.letter(null)).isEmpty();
        assertThat(UploadValidator.letter(List.of(file("", new byte[0])))).isEmpty();
    }

    @Test
    void detectsTypesBySignatureNotName() {
        var result = UploadValidator.letter(List.of(file("scan.pdf", TestData.PNG), file("b.jpg", TestData.PNG)));
        assertThat(result).extracting(UploadValidator.ValidFile::contentType).containsExactly("image/png", "image/png");
        assertThat(UploadValidator.letter(List.of(file("x.bin", TestData.PDF))).getFirst().contentType())
                .isEqualTo("application/pdf");
    }

    @Test
    void limitsPageCount() {
        List<MultipartFile> pages = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            pages.add(file("p" + i + ".png", TestData.PNG));
        }
        assertThatThrownBy(() -> UploadValidator.letter(pages)).hasMessageContaining("at most 20 pages");
    }

    @ParameterizedTest
    @CsvSource({
            "Wedding Letter.pdf, Wedding Letter.pdf",
            "C:\\\\Users\\\\me\\\\card.pdf, card.pdf",
            "../../etc/passwd, passwd",
            "\"quoted\";.pdf, _quoted__.pdf",
            "കത്ത്.pdf, _____.pdf",
            "..., letter",
    })
    void sanitisesFilenames(String original, String expected) {
        assertThat(UploadValidator.safeName(original, "letter")).isEqualTo(expected);
    }
}
