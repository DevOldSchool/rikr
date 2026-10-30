package org.de.rikr.ui;

import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * The colour of secondary text in list rows, such as an owner class or how a reference is used, as an HTML colour.
 * On a selected row it takes the selection's text colour, so it stays readable on the selection background.
 */
final class SecondaryText {
    private SecondaryText() {
    }

    static String color(JList<?> list, boolean selected) {
        Color color = selected ? list.getSelectionForeground() : Theme.CONTENT_HIGHLIGHT_TEXT_COLOR;
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
