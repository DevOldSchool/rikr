package org.de.rikr.theme;

/**
 * Contract for pluggable themes.
 */
public interface ThemeProvider {
    /**
     * Unique stable id persisted in settings (e.g., "dark.flatij").
     */
    String id();

    /**
     * Pretty name for UI display (e.g., "Dark").
     */
    String displayName();

    /**
     * Apply the underlying Look & Feel (e.g., FlatLaf theme).
     */
    void setupLookAndFeel();

    /**
     * Provide the color palette for this theme.
     */
    ThemePalette palette();

    /**
     * For combo boxes and logs, default to displayName.
     */
    default String toStringDisplay() {
        return displayName();
    }
}
