package org.de.rikr.ui;

import javax.swing.*;
import java.awt.*;

public class ScrollableTextPane extends JTextPane implements Scrollable {

    public ScrollableTextPane() {
        super();
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        FontMetrics fontMetrics = getFontMetrics(getFont());
        return fontMetrics.getHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        if (getParent() instanceof JViewport viewport) {
            return viewport.getWidth() > getUI().getPreferredSize(this).width;
        }
        
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (getParent() instanceof JViewport viewport) {
            return viewport.getHeight() > getUI().getPreferredSize(this).height;
        }

        return false;
    }
}
