package org.de.rikr.resources;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Fonts {
    private static final Map<String, Font> FONTS = new HashMap<>();

    public static void loadFonts() {
        // Define font paths and their associated names
        String[][] fontData = {
            {"JetBrainsMono-Regular", "/fonts/JetBrainsMono-Regular.ttf"},
        };

        // Load custom fonts
        for (String[] data : fontData) {
            String fontName = data[0];
            String fontPath = data[1];

            Font font = loadFont(fontPath).deriveFont(13f);
            FONTS.put(fontName, font);
        }
    }

    public static Font loadFont(String path) {
        try {
            try (InputStream inputStream = Fonts.class.getResourceAsStream(path)) {
                if (inputStream == null) {
                    throw new IOException("Font file not found: " + path);
                }

                Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(font);
                return font;
            }
        } catch (FontFormatException | IOException e) {
            throw new IllegalStateException("Failed to load font: " + path, e);
        }
    }

    public static Font getDefaultFont() {
        return FONTS.get("JetBrainsMono-Regular");
    }
}
