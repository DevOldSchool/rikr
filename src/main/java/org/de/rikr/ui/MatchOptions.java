package org.de.rikr.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Match Case and Match Words as toggles inside a search field, after the text as JetBrains IDEs show them, with a
 * search icon before the text and a button that clears it. Alt+C and Alt+W switch them from the keyboard.
 */
final class MatchOptions {
    private final JToggleButton matchCase = toggle("Cc", "Match Case (Alt+C)", "Match Case");
    private final JToggleButton matchWord = toggle("W", "Match Words (Alt+W)", "Match Words");

    /**
     * @param changed told when an option is switched, with these options
     */
    MatchOptions(JTextField field, Consumer<MatchOptions> changed) {
        JToolBar options = new JToolBar();
        options.setFloatable(false);
        options.setOpaque(false);
        options.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        options.add(matchCase);
        options.add(matchWord);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, options);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        matchCase.addActionListener(event -> changed.accept(this));
        matchWord.addActionListener(event -> changed.accept(this));
        bind(field, KeyEvent.VK_C, "toggleMatchCase", matchCase);
        bind(field, KeyEvent.VK_W, "toggleMatchWords", matchWord);
    }

    boolean matchCase() {
        return matchCase.isSelected();
    }

    boolean matchWord() {
        return matchWord.isSelected();
    }

    /**
     * The icons take their colour from the theme as they paint, so nothing changes here; kept so callers can treat
     * every search field the same when the theme changes.
     */
    void applyTheme() {
        matchCase.repaint();
        matchWord.repaint();
    }

    private static JToggleButton toggle(String label, String tooltip, String name) {
        JToggleButton button = new JToggleButton(new LabelIcon(label));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        // Small enough that the selected background sits inside the field's edge.
        button.setMargin(new Insets(1, 2, 1, 2));
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleName(name);
        // Typing stays in the field; the toggles are reached with Alt+C and Alt+W, as in JetBrains IDEs.
        button.setFocusable(false);
        return button;
    }

    private static void bind(JTextField field, int key, String name, JToggleButton button) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, InputEvent.ALT_DOWN_MASK), name);
        field.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                button.doClick();
            }
        });
    }

    /**
     * A short label drawn as an icon, such as "Cc", in the label colour of the current theme. Drawn rather than an
     * image so it stays sharp on high-density screens and matches the field's text size.
     */
    private static final class LabelIcon implements Icon {
        private static final int SIZE = 16;
        private final String label;

        LabelIcon(String label) {
            this.label = label;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Font font = component.getFont().deriveFont(Font.BOLD, 12f);
            g.setFont(font);
            g.setColor(component.isEnabled() ? UIManager.getColor("Label.foreground")
                : UIManager.getColor("Label.disabledForeground"));
            FontMetrics metrics = g.getFontMetrics();
            int textX = x + (getIconWidth() - metrics.stringWidth(label)) / 2;
            int textY = y + (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(label, textX, textY);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return label.length() > 1 ? SIZE + 2 : SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
