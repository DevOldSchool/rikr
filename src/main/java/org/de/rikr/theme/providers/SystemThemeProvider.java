package org.de.rikr.theme.providers;

import org.de.rikr.theme.SystemAppearance;
import org.de.rikr.theme.ThemePalette;
import org.de.rikr.theme.ThemeProvider;

/**
 * Follows the operating system's appearance: the dark theme while it is dark, the light theme otherwise. Which
 * one is decided each time the look and feel is set up, so a change of appearance is picked up on the next apply.
 */
public class SystemThemeProvider implements ThemeProvider {
    public static final String ID = "system";

    private final ThemeProvider dark = new DarkThemeProvider();
    private final ThemeProvider light = new LightThemeProvider();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Match system";
    }

    /**
     * The theme the system appearance currently calls for.
     */
    public ThemeProvider resolved() {
        return SystemAppearance.isDark() ? dark : light;
    }

    @Override
    public void setupLookAndFeel() {
        resolved().setupLookAndFeel();
    }

    @Override
    public ThemePalette palette() {
        return resolved().palette();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
