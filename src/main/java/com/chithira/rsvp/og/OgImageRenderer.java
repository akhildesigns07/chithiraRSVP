package com.chithira.rsvp.og;

import com.chithira.rsvp.event.CardTemplate;
import com.chithira.rsvp.event.Event;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Draws the 1200x630 Open Graph image for an event: photo, occasion, couple names, date and time,
 * in the colours of the event's card design. Takes only event-level data, so guest names and phone
 * numbers cannot end up in the image.
 */
@Component
public class OgImageRenderer {

    public static final int WIDTH = 1200;
    public static final int HEIGHT = 630;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    /** Photos are decoded at most this large (longest side), whatever their stored size. */
    private static final int MAX_DECODE = 1600;

    private record Palette(Color bgTop, Color bgBottom, Color card, Color accent, Color ink, Color gold) {
    }

    private static final Map<CardTemplate, Palette> PALETTES = Map.of(
            CardTemplate.KERALA_TRADITIONAL, new Palette(new Color(0x85203a), new Color(0x4a0b17),
                    new Color(0xfdf5e2), new Color(0x7a1024), new Color(0x4a1220), new Color(0xc09030)),
            CardTemplate.CHRISTIAN_ELEGANT, new Palette(new Color(0xf7f0e2), new Color(0xe7d9bc),
                    new Color(0xfffdf7), new Color(0x85652a), new Color(0x3b3024), new Color(0xb9974f)),
            CardTemplate.NIKAH_CLASSIC, new Palette(new Color(0x0f5a45), new Color(0x042319),
                    new Color(0x0b4636), new Color(0xe6c67d), new Color(0xf5eed8), new Color(0xd4af5a)));

    private final Font baseFont;

    public OgImageRenderer() {
        try (InputStream in = new ClassPathResource("fonts/NotoSerifMalayalam.ttf").getInputStream()) {
            baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Cannot load the share-image font", e);
        }
    }

    /** Every piece of text drawn on the image, in order. */
    public List<String> textLines(Event event) {
        return List.of(
                (event.getEventType().getLabelEn() + " · " + event.getEventType().getLabelMl()).toUpperCase(Locale.ENGLISH),
                event.getCoupleNames(),
                DATE.format(event.getStartsAt()),
                TIME.format(event.getStartsAt()));
    }

    public byte[] render(Event event, Optional<byte[]> photoBytes) {
        Palette p = PALETTES.get(event.getTemplate());
        Optional<BufferedImage> photo = photoBytes.flatMap(OgImageRenderer::decode);
        List<String> text = textLines(event);

        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            g.setPaint(new GradientPaint(0, 0, p.bgTop(), 0, HEIGHT, p.bgBottom()));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int inset = 28;
            var card = new RoundRectangle2D.Float(inset, inset, WIDTH - 2 * inset, HEIGHT - 2 * inset, 16, 16);
            g.setColor(p.card());
            g.fill(card);
            drawFrame(g, event.getTemplate(), p, inset);

            int textLeft;
            int textRight = WIDTH - 72;
            if (photo.isPresent()) {
                Shape clip = photoShape(event.getTemplate(), 72, 72, 420, HEIGHT - 144);
                drawCover(g, photo.get(), clip);
                g.setColor(p.gold());
                g.setStroke(new BasicStroke(4f));
                g.draw(clip);
                textLeft = 72 + 420 + 40;
            } else {
                textLeft = 96;
                textRight = WIDTH - 96;
            }
            drawText(g, p, text, textLeft, textRight);
        } finally {
            g.dispose();
        }
        return jpeg(img);
    }

    private void drawFrame(Graphics2D g, CardTemplate template, Palette p, int inset) {
        float x = inset + 12;
        float y = inset + 12;
        float w = WIDTH - 2 * x;
        float h = HEIGHT - 2 * y;
        switch (template) {
            case KERALA_TRADITIONAL -> {
                // Kasavu band: gold, light gold, a maroon thread, gold.
                strokeRect(g, new Color(0xa77a22), 10f, x, y, w, h);
                strokeRect(g, new Color(0xf3d27a), 5f, x, y, w, h);
                strokeRect(g, p.accent(), 1.5f, x, y, w, h);
            }
            case CHRISTIAN_ELEGANT -> {
                strokeRect(g, p.gold(), 1.5f, x, y, w, h);
                strokeRect(g, p.gold(), 1.5f, x + 6, y + 6, w - 12, h - 12);
            }
            case NIKAH_CLASSIC -> {
                strokeRect(g, p.gold(), 1.5f, x, y, w, h);
                strokeRect(g, p.gold(), 1.5f, x + 5, y + 5, w - 10, h - 10);
            }
        }
    }

    private static void strokeRect(Graphics2D g, Color c, float width, float x, float y, float w, float h) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        g.draw(new Rectangle2D.Float(x, y, w, h));
    }

    private static Shape photoShape(CardTemplate template, int x, int y, int w, int h) {
        return switch (template) {
            case NIKAH_CLASSIC -> {
                // Arch: semicircular top on a rectangle.
                var arch = new Area(new Rectangle2D.Float(x, y + w / 2f, w, h - w / 2f));
                arch.add(new Area(new Ellipse2D.Float(x, y, w, w)));
                yield arch;
            }
            case CHRISTIAN_ELEGANT -> new RoundRectangle2D.Float(x, y, w, h, w, w);
            case KERALA_TRADITIONAL -> new RoundRectangle2D.Float(x, y, w, h, 24, 24);
        };
    }

    /** Scales the photo to cover the shape's bounds (centre crop) and clips it to the shape. */
    private static void drawCover(Graphics2D g, BufferedImage photo, Shape clip) {
        Rectangle2D b = clip.getBounds2D();
        double scale = Math.max(b.getWidth() / photo.getWidth(), b.getHeight() / photo.getHeight());
        double dw = photo.getWidth() * scale;
        double dh = photo.getHeight() * scale;
        Shape old = g.getClip();
        g.clip(clip);
        g.drawImage(photo, (int) Math.round(b.getCenterX() - dw / 2), (int) Math.round(b.getCenterY() - dh / 2),
                (int) Math.ceil(dw), (int) Math.ceil(dh), null);
        g.setClip(old);
    }

    private void drawText(Graphics2D g, Palette p, List<String> text, int left, int right) {
        int width = right - left;
        float cx = left + width / 2f;

        Font occasion = baseFont.deriveFont(Map.of(TextAttribute.SIZE, 24f, TextAttribute.TRACKING, 0.12f,
                TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD));
        Font date = baseFont.deriveFont(34f);
        Font time = baseFont.deriveFont(Font.BOLD, 44f);

        // Names: one line if it can be large, else two, else three smaller lines; never drop a name
        // unless even that fails (then the last line is shortened).
        Font names = null;
        List<String> nameLines = List.of();
        float[][] attempts = {{84, 60, 1}, {76, 44, 2}, {44, 34, 3}};
        search:
        for (float[] attempt : attempts) {
            for (float size = attempt[0]; size >= attempt[1]; size -= 4) {
                names = baseFont.deriveFont(Font.BOLD, size);
                nameLines = wrap(g, names, text.get(1), width, (int) attempt[2]);
                if (!nameLines.isEmpty()) {
                    break search;
                }
            }
        }
        if (nameLines.isEmpty()) {
            nameLines = ellipsize(g, names, text.get(1), width);
        }

        int namesLineHeight = (int) (names.getSize2D() * 1.15f);
        int blockHeight = 36 + 20 + nameLines.size() * namesLineHeight + 34 + 48 + 56;
        float y = (HEIGHT - blockHeight) / 2f + 30;

        g.setColor(p.accent());
        drawCentered(g, occasion, text.get(0), cx, y);
        y += 36 + 20;

        g.setColor(p.accent());
        for (String line : nameLines) {
            y += namesLineHeight * 0.85f;
            drawCentered(g, names, line, cx, y);
            y += namesLineHeight * 0.15f;
        }

        y += 30;
        g.setColor(p.gold());
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine((int) cx - 120, (int) y, (int) cx - 14, (int) y);
        g.drawLine((int) cx + 14, (int) y, (int) cx + 120, (int) y);
        var diamond = new Path2D.Float();
        diamond.moveTo(cx, y - 7);
        diamond.lineTo(cx + 7, y);
        diamond.lineTo(cx, y + 7);
        diamond.lineTo(cx - 7, y);
        diamond.closePath();
        g.fill(diamond);

        y += 56;
        g.setColor(p.ink());
        drawCentered(g, date, fit(g, date, text.get(2), width), cx, y);
        y += 58;
        g.setColor(p.accent());
        drawCentered(g, time, text.get(3), cx, y);
    }

    private static void drawCentered(Graphics2D g, Font font, String s, float cx, float baseline) {
        g.setFont(font);
        float w = (float) font.getStringBounds(s, g.getFontRenderContext()).getWidth();
        g.drawString(s, cx - w / 2f, baseline);
    }

    /** Word-wraps into at most {@code maxLines} lines, or returns empty if it doesn't fit. */
    private static List<String> wrap(Graphics2D g, Font font, String s, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.trim().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (widthOf(g, font, candidate) <= width) {
                line = new StringBuilder(candidate);
            } else {
                if (line.isEmpty() || widthOf(g, font, word) > width) {
                    return List.of();
                }
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        lines.add(line.toString());
        return lines.size() <= maxLines ? lines : List.of();
    }

    private static List<String> ellipsize(Graphics2D g, Font font, String s, int width) {
        return List.of(fit(g, font, s, width));
    }

    private static String fit(Graphics2D g, Font font, String s, int width) {
        if (widthOf(g, font, s) <= width) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && widthOf(g, font, t + "…") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t.strip() + "…";
    }

    private static double widthOf(Graphics2D g, Font font, String s) {
        return font.getStringBounds(s, g.getFontRenderContext()).getWidth();
    }

    /**
     * Decodes JPEG/PNG with subsampling so a small file with huge dimensions can't exhaust memory.
     * Formats ImageIO can't read (e.g. WebP) yield empty, and the image is drawn without a photo.
     */
    static Optional<BufferedImage> decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int longest = Math.max(reader.getWidth(0), reader.getHeight(0));
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, (int) Math.ceil(longest / (double) MAX_DECODE));
                param.setSourceSubsampling(step, step, 0, 0);
                return Optional.ofNullable(reader.read(0, param));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static byte[] jpeg(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(img, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
