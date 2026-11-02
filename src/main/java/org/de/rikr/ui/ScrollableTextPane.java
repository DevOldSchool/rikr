package org.de.rikr.ui;

import javax.swing.*;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;
import java.awt.*;

public class ScrollableTextPane extends JTextPane implements Scrollable {

    public ScrollableTextPane() {
        super();
        setEditorKit(new CodeEditorKit());
    }

    // A class can be a hundred thousand lines; StyledLinesView shows them without a view for each.
    private static final class CodeEditorKit extends StyledEditorKit {
        @Override
        public ViewFactory getViewFactory() {
            return StyledLinesView.factory();
        }
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
            int viewportWidth = viewport.getWidth();
            int preferredWidth = getUI().getPreferredSize(this).width;
            return viewportWidth == 0 || viewportWidth > preferredWidth;
        }

        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        if (getParent() instanceof JViewport viewport) {
            int viewportHeight = viewport.getHeight();
            int preferredHeight = getUI().getPreferredSize(this).height;
            return viewportHeight == 0 || viewportHeight > preferredHeight;
        }

        return false;
    }
}
