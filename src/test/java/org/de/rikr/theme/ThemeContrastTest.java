package org.de.rikr.theme;

import org.de.rikr.theme.laf.LafDefaults;
import org.de.rikr.theme.providers.DarkThemeProvider;
import org.de.rikr.theme.providers.LightThemeProvider;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks each theme's colour pairs against WCAG 2.2: 4.5:1 for text (SC 1.4.3) and 3:1 for control edges, focus
 * rings and icons (SC 1.4.11). A new colour that fails these fails the build instead of shipping.
 */
class ThemeContrastTest {
    private static final double TEXT = 4.5;
    private static final double NON_TEXT = 3.0;

    @Test
    void darkThemeMeetsContrastMinimums() {
        assertPairs(new DarkThemeProvider().palette(), LafDefaults.DARK_CONTROL_BORDER, LafDefaults.DARK_FOCUS);
    }

    @Test
    void lightThemeMeetsContrastMinimums() {
        assertPairs(new LightThemeProvider().palette(), LafDefaults.LIGHT_CONTROL_BORDER, LafDefaults.LIGHT_FOCUS);
    }

    @Test
    void contrastRatioMatchesTheWcagFormula() {
        assertTrue(Math.abs(contrast(Color.BLACK, Color.WHITE) - 21.0) < 0.01);
        assertTrue(Math.abs(contrast(Color.WHITE, Color.WHITE) - 1.0) < 0.01);
    }

    private static void assertPairs(ThemePalette palette, Color controlBorder, Color focus) {
        Color panel = palette.BACKGROUND_COLOR;
        Color content = palette.CONTENT_BACKGROUND_COLOR;
        List<String> failures = new ArrayList<>();

        check(failures, "text on panel", palette.CONTENT_TEXT_COLOR, panel, TEXT);
        check(failures, "text on content", palette.CONTENT_TEXT_COLOR, content, TEXT);
        check(failures, "secondary text on panel", palette.CONTENT_HIGHLIGHT_TEXT_COLOR, panel, TEXT);
        check(failures, "secondary text on content", palette.CONTENT_HIGHLIGHT_TEXT_COLOR, content, TEXT);
        check(failures, "code text", palette.SYNTAX_HIGHLIGHT_DEFAULT_COLOR, content, TEXT);
        check(failures, "keyword", palette.SYNTAX_HIGHLIGHT_KEYWORD_COLOR, content, TEXT);
        check(failures, "comment", palette.SYNTAX_HIGHLIGHT_COMMENT_COLOR, content, TEXT);
        check(failures, "number", palette.SYNTAX_HIGHLIGHT_NUMBER_COLOR, content, TEXT);
        check(failures, "string", palette.SYNTAX_HIGHLIGHT_STRING_COLOR, content, TEXT);
        check(failures, "annotation", palette.SYNTAX_HIGHLIGHT_ANNOTATION_COLOR, content, TEXT);
        check(failures, "code text on a search match", palette.SYNTAX_HIGHLIGHT_DEFAULT_COLOR,
            palette.SEARCH_HIGHLIGHT_COLOR, TEXT);

        check(failures, "control edge on panel", controlBorder, panel, NON_TEXT);
        check(failures, "focus ring on panel", focus, panel, NON_TEXT);
        check(failures, "selected navigation icon", Color.WHITE, palette.NAVBAR_BUTTON_SELECTED_COLOR, NON_TEXT);

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static void check(List<String> failures, String name, Color foreground, Color background, double minimum) {
        double ratio = contrast(foreground, background);
        if (ratio < minimum) {
            failures.add(String.format("%s: %.2f:1, needs %.1f:1", name, ratio, minimum));
        }
    }

    static double contrast(Color first, Color second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
