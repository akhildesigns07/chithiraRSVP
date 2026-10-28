package com.chithira.rsvp.og;

import static org.assertj.core.api.Assertions.assertThat;

import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.event.EventType;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OgImageRendererTest {

    /** WhatsApp skips link-preview images larger than about 300 KB. */
    private static final int WHATSAPP_LIMIT = 300 * 1024;
    private static final Path SAMPLES = Path.of("target", "og-samples");

    private final OgImageRenderer renderer = new OgImageRenderer();

    static Event event(CardTemplate template, String names) {
        var e = new Event();
        e.setCoupleNames(names);
        e.setEventType(EventType.WEDDING);
        e.setStartsAt(LocalDateTime.of(2027, 2, 14, 10, 30));
        e.setVenue("St. Mary's Church, Kottayam");
        e.setTemplate(template);
        e.setMessageEn("m");
        e.setMessageMl("m");
        return e;
    }

    static byte[] photo(int w, int h, String format) throws IOException {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xd9826b), w, h, new Color(0x6b8fd9)));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.fillOval(w / 3, h / 4, w / 3, h / 2);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] jpeg) throws IOException {
        assertThat(jpeg).startsWith(0xFF, 0xD8, 0xFF);
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static void sample(String name, byte[] jpeg) throws IOException {
        Files.createDirectories(SAMPLES);
        Files.write(SAMPLES.resolve(name), jpeg);
    }

    @ParameterizedTest
    @EnumSource(CardTemplate.class)
    void rendersA1200x630JpegSmallEnoughForWhatsApp(CardTemplate template) throws IOException {
        byte[] jpeg = renderer.render(event(template, "Anjali & Rahul"), Optional.of(photo(900, 1200, "jpeg")));
        sample(template.getSlug() + ".jpg", jpeg);

        BufferedImage img = read(jpeg);
        assertThat(img.getWidth()).isEqualTo(1200);
        assertThat(img.getHeight()).isEqualTo(630);
        assertThat(jpeg.length).isLessThan(WHATSAPP_LIMIT);
    }

    @Test
    void worksWithoutAPhotoAndWithUnreadablePhotos() throws IOException {
        var event = event(CardTemplate.NIKAH_CLASSIC, "Fathima & Shahid");
        sample("no-photo.jpg", renderer.render(event, Optional.empty()));

        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1, 2, 3};
        assertThat(read(renderer.render(event, Optional.of(webp))).getWidth()).isEqualTo(1200);
        assertThat(read(renderer.render(event, Optional.of(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}))).getWidth())
                .isEqualTo(1200);
    }

    @Test
    void hugePhotoDimensionsAreSubsampledNotFullyDecoded() throws IOException {
        byte[] big = photo(6000, 4000, "jpeg");
        assertThat(OgImageRenderer.decode(big)).get()
                .satisfies(img -> assertThat(Math.max(img.getWidth(), img.getHeight())).isLessThanOrEqualTo(1600));
    }

    @Test
    void handlesMalayalamAndVeryLongNames() throws IOException {
        sample("malayalam.jpg", renderer.render(event(CardTemplate.KERALA_TRADITIONAL, "അഞ്ജലി & രാഹുൽ"), Optional.empty()));
        byte[] longNames = renderer.render(event(CardTemplate.CHRISTIAN_ELEGANT,
                "Mariamma Elizabeth Kurian & Thomas Varghese Puthenpurackal Chacko"), Optional.of(photo(800, 800, "png")));
        sample("long-names.jpg", longNames);
        assertThat(read(longNames).getWidth()).isEqualTo(1200);
    }

    @Test
    void imageTextIsEventLevelOnly() {
        var event = event(CardTemplate.KERALA_TRADITIONAL, "Anjali & Rahul");
        assertThat(renderer.textLines(event))
                .containsExactly("WEDDING · വിവാഹം", "Anjali & Rahul", "Sunday, 14 February 2027", "10:30 AM");
        // The renderer's API takes an Event and photo bytes only: it has no way to receive a guest.
        assertThat(OgImageRenderer.class.getDeclaredMethods())
                .allSatisfy(m -> assertThat(m.getParameterTypes())
                        .doesNotContain(com.chithira.rsvp.guest.Guest.class));
    }
}
