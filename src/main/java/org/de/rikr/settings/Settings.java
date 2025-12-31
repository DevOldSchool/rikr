package org.de.rikr.settings;

import org.de.rikr.resources.Fonts;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;


public class Settings {
    private static final String KEY_FONT_FAMILY = "font.family";
    private static final String KEY_FONT_SIZE = "font.size";
    private static final String KEY_THEME_ID = "theme.id";

    private String fontFamily;
    private int fontSize;
    private String themeId;

    public Settings() {
        this.fontFamily = Fonts.getDefaultFont() != null ? Fonts.getDefaultFont().getFamily() : "JetBrains Mono";
        this.fontSize = Fonts.getDefaultFont() != null ? Fonts.getDefaultFont().getSize() : 13;
        this.themeId = "dark.flatij";
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }


    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public static class Store {
        private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".rikr");
        private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.properties");

        public static Settings load() {
            Settings settings = new Settings();
            if (!Files.exists(CONFIG_FILE)) {
                try {
                    Files.createDirectories(CONFIG_DIR);
                    save(settings);
                } catch (IOException ignored) {
                }

                return settings;
            }

            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException ignored) {
                return settings;
            }

            settings.fontFamily = props.getProperty(KEY_FONT_FAMILY, settings.fontFamily);
            try {
                settings.fontSize = Integer.parseInt(props.getProperty(KEY_FONT_SIZE, Integer.toString(settings.fontSize)));
            } catch (NumberFormatException ignored) {
            }

            String loadedId = props.getProperty(KEY_THEME_ID, null);
            if (loadedId != null && !loadedId.isEmpty()) {
                settings.themeId = loadedId;
            }

            return settings;
        }

        public static void save(Settings settings) {
            Properties props = new Properties();
            props.setProperty(KEY_FONT_FAMILY, settings.fontFamily != null ? settings.fontFamily : "");
            props.setProperty(KEY_FONT_SIZE, Integer.toString(settings.fontSize));
            String id = (settings.themeId != null && !settings.themeId.isEmpty()) ? settings.themeId : "dark.flatij";
            props.setProperty(KEY_THEME_ID, id);

            try {
                Files.createDirectories(CONFIG_DIR);
                Path tmp = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName().toString() + ".tmp");
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    props.store(out, "Rikr Settings");
                }
                Files.move(tmp, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
                    props.store(out, "Rikr Settings");
                } catch (IOException ignored) {
                }
            }
        }
    }
}
