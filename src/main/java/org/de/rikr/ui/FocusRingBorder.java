package org.de.rikr.ui;

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * A border that is invisible until its component has keyboard focus, then a 2px ring in the theme's focus colour
 * (WCAG 2.2 SC 2.4.7). For icon buttons that paint no border of their own, such as the workspace buttons, which
 * otherwise give no sign that Tab has reached them.
 */
public class FocusRingBorder extends AbstractBorder {
    private static final int INSET = 4;
    private final boolean onAccent;

    public FocusRingBorder() {
        this(false);
    }

    /**
     * @param onAccent the component is filled with the accent colour, so the ring is white to stay visible
     */
    public FocusRingBorder(boolean onAccent) {
        this.onAccent = onAccent;
    }

    @Override
    public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        if (!component.hasFocus()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color focus = UIManager.getColor("Component.focusColor");
        g.setColor(onAccent || focus == null ? Color.WHITE : focus);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 6, 6);
        g.dispose();
    }

    @Override
    public Insets getBorderInsets(Component component, Insets insets) {
        insets.set(INSET, INSET, INSET, INSET);
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}
