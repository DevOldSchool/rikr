package org.de.rikr.ui;

import org.de.rikr.resources.Images;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * One look for every search field: a visible edge and a hint saying what it searches.
 */
final class SearchFields {
    // At least 3:1 against the panel and the field in each theme (WCAG 2.2 SC 1.4.11).
    private static final Color DARK_BORDER = new Color(0x7a, 0x7e, 0x85);
    private static final Color LIGHT_BORDER = new Color(0x80, 0x83, 0x89);

    private SearchFields() {
    }

    static void style(JTextField field, String hint) {
        field.putClientProperty("JTextField.placeholderText", hint);
        field.getAccessibleContext().setAccessibleName(hint);
        applyTheme(field);
    }

    /**
     * A match option's icon: light on the accent colour when it is on, otherwise the theme's icon colour.
     */
    static void toggleIcon(JButton button, String icon, boolean on) {
        button.setIcon(on ? Images.getImage(icon) : Images.getThemedImage(icon));
    }

    /**
     * Pick the border for the current theme; call again when the theme changes.
     */
    static void applyTheme(JTextField field) {
        Color background = Theme.BACKGROUND_COLOR;
        boolean dark = background.getRed() + background.getGreen() + background.getBlue() < 3 * 128;
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(dark ? DARK_BORDER : LIGHT_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
    }
}
