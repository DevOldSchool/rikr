package org.de.rikr.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FocusRingBorderTest {
    /**
     * A button that reports focus without a window, since focus needs a visible frame.
     */
    private static final class FocusedButton extends JButton {
        private final boolean focused;

        FocusedButton(boolean focused) {
            this.focused = focused;
            setSize(28, 28);
        }

        @Override
        public boolean hasFocus() {
            return focused;
        }
    }

    @Test
    void paintsARingOnlyWhileFocused() {
        assertNotEquals(0, ringPixel(new FocusedButton(true), false) >>> 24, "ring expected with focus");
        assertEquals(0, ringPixel(new FocusedButton(false), false) >>> 24, "no ring expected without focus");
    }

    @Test
    void ringIsWhiteOnTheAccentFill() {
        assertEquals(Color.WHITE.getRGB(), ringPixel(new FocusedButton(true), true));
    }

    private static int ringPixel(JButton button, boolean onAccent) {
        BufferedImage image = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        new FocusRingBorder(onAccent).paintBorder(button, g, 0, 0, 28, 28);
        g.dispose();
        // The ring is a 2px stroke drawn 1px in from the edge, so the second pixel row of a side is on it.
        return image.getRGB(14, 1);
    }
}
