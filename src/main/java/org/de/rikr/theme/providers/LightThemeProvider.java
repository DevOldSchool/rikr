package org.de.rikr.theme.providers;

import com.formdev.flatlaf.intellijthemes.FlatLightFlatIJTheme;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemePalette;
import org.de.rikr.theme.ThemeProvider;

/**
 * Built-in Light theme provider backed by FlatLaf Flat Light IJ theme.
 */
public class LightThemeProvider implements ThemeProvider {
    @Override
    public String id() {
        return "light.flatij";
    }

    @Override
    public String displayName() {
        return "Light";
    }

    @Override
    public void setupLookAndFeel() {
        FlatLightFlatIJTheme.setup();
    }

    @Override
    public ThemePalette palette() {
        return new ThemePalette(
            Theme.LIGHT_BACKGROUND_COLOR,
            Theme.LIGHT_CONTENT_BACKGROUND_COLOR,
            Theme.LIGHT_NAVBAR_BUTTON_SELECTED_COLOR,
            Theme.LIGHT_SEARCH_BUTTON_SELECTED_COLOR,
            Theme.LIGHT_CONTENT_SELECTED_HIGHLIGHT_COLOR,
            Theme.LIGHT_SEARCH_HIGHLIGHT_COLOR,
            Theme.LIGHT_CONTENT_TEXT_COLOR,
            Theme.LIGHT_CONTENT_BORDER_COLOR,
            Theme.LIGHT_CONTENT_HIGHLIGHT_TEXT_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_DEFAULT_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_KEYWORD_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_COMMENT_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_NUMBER_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_STRING_COLOR,
            Theme.LIGHT_SYNTAX_HIGHLIGHT_ANNOTATION_COLOR
        );
    }

    @Override
    public String toString() {
        return displayName();
    }
}
