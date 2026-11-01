package org.de.rikr.ui;

import org.de.rikr.theme.Theme;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * One look for every search field: a rounded edge that turns the accent colour while the field has focus, and a hint
 * saying what it searches.
 */
final class SearchFields {
    // At least 3:1 against the panel and the field in each theme (WCAG 2.2 SC 1.4.11), for the edge and for focus.
    private static final Color DARK_BORDER = new Color(0x7a, 0x7e, 0x85);
    private static final Color LIGHT_BORDER = new Color(0x80, 0x83, 0x89);
    private static final Color DARK_FOCUS = new Color(0x54, 0x8a, 0xf7);
    private static final Color LIGHT_FOCUS = new Color(0x35, 0x74, 0xf0);
    // Tall enough to hit easily, and to hold the 24-pixel match toggles inside the edge.
    static final int MINIMUM_HEIGHT = 28;

    private SearchFields() {
    }

    static void style(JTextField field, String hint) {
        field.putClientProperty("JTextField.placeholderText", hint);
        field.getAccessibleContext().setAccessibleName(hint);
        // Search fields draw their own rounded edge; repaint so it follows focus.
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                field.repaint();
            }
        });
        applyTheme(field);
    }

    /**
     * Pick the edge colours for the current theme; call again when the theme changes.
     */
    static void applyTheme(JTextField field) {
        Color background = Theme.BACKGROUND_COLOR;
        boolean dark = background.getRed() + background.getGreen() + background.getBlue() < 3 * 128;
        field.setBorder(new Edge(dark ? DARK_BORDER : LIGHT_BORDER, dark ? DARK_FOCUS : LIGHT_FOCUS));
    }

    private static final class Edge extends AbstractBorder {
        private static final int ARC = 8;
        private final Color color;
        private final Color focus;

        Edge(Color color, Color focus) {
            this.color = color;
            this.focus = focus;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focused = component.hasFocus();
            float stroke = focused ? 2f : 1f;
            g.setColor(focused ? focus : color);
            g.setStroke(new BasicStroke(stroke));
            float inset = stroke / 2;
            g.draw(new RoundRectangle2D.Float(x + inset, y + inset, width - stroke, height - stroke, ARC, ARC));
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            // The edge sets the height: FlatLaf ignores its minimum height for a border of our own.
            int text = component.getFontMetrics(component.getFont()).getHeight();
            int vertical = Math.max(2, (MINIMUM_HEIGHT - text + 1) / 2);
            insets.set(vertical, 6, vertical, 3);
            return insets;
        }
    }
}
