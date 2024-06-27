package org.de.rikr.loader;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class FontLoader {

    public static Font[] loadFonts(String[] paths) {
        Font[] fonts = new Font[paths.length];
        try {
            for (int i = 0; i < paths.length; i++) {
                try (InputStream inputStream = FontLoader.class.getResourceAsStream(paths[i])) {
                    if (inputStream == null) {
                        throw new IOException("Font file not found: " + paths[i]);
                    }

                    Font font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                    ge.registerFont(font);
                    fonts[i] = font;
                }
            }
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
        }

        return fonts;
    }
}