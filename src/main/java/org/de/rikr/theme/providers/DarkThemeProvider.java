package org.de.rikr.theme.providers;

import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemePalette;
import org.de.rikr.theme.ThemeProvider;

/**
 * Built-in Dark theme provider backed by FlatLaf Flat Dark IJ theme.
 */
public class DarkThemeProvider implements ThemeProvider {
    @Override
    public String id() {
        return "dark.flatij";
    }

    @Override
    public String displayName() {
        return "Dark";
    }

    @Override
    public void setupLookAndFeel() {
        FlatDarkFlatIJTheme.setup();
    }

    @Override
    public ThemePalette palette() {
        return new ThemePalette(
            Theme.DARK_BACKGROUND_COLOR,
            Theme.DARK_CONTENT_BACKGROUND_COLOR,
            Theme.DARK_NAVBAR_BUTTON_SELECTED_COLOR,
            Theme.DARK_SEARCH_BUTTON_SELECTED_COLOR,
            Theme.DARK_CONTENT_SELECTED_HIGHLIGHT_COLOR,
            Theme.DARK_SEARCH_HIGHLIGHT_COLOR,
            Theme.DARK_CONTENT_TEXT_COLOR,
            Theme.DARK_CONTENT_BORDER_COLOR,
            Theme.DARK_CONTENT_HIGHLIGHT_TEXT_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_DEFAULT_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_KEYWORD_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_COMMENT_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_NUMBER_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_STRING_COLOR,
            Theme.DARK_SYNTAX_HIGHLIGHT_ANNOTATION_COLOR
        );
    }

    @Override
    public String toString() {
        return displayName();
    }
}
