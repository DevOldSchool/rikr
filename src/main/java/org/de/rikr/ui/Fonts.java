package org.de.rikr.ui;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class Fonts {
    public static HashMap<String, Font> fonts = new HashMap<>();

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
            fonts.put(fontName, font);
        }
    }

    public static Font loadFont(String path) {
        Font font = null;

        try {
            try (InputStream inputStream = Fonts.class.getResourceAsStream(path)) {
                if (inputStream == null) {
                    throw new IOException("Font file not found: " + path);
                }

                font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                ge.registerFont(font);
            }
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
        }

        return font;
    }

    public static Font getDefaultFont() {
        return fonts.get("JetBrainsMono-Regular");
    }
}
