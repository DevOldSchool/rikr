package org.de.rikr.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} whose preferred height follows the rows its children wrap into. A plain FlowLayout always
 * reports one row, so a parent that gives it its preferred height clips every row after the first.
 */
public class WrapLayout extends FlowLayout {
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    /**
     * Lay the container's parent out again whenever the container's width changes, since the rows, and so the
     * height the parent must give it, depend on that width. Call once, when the container is made.
     */
    public static void followWidth(Container target) {
        target.addComponentListener(new java.awt.event.ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                if (target.getWidth() == lastWidth) {
                    return;
                }
                lastWidth = target.getWidth();
                Container parent = target.getParent();
                if (parent != null) {
                    javax.swing.SwingUtilities.invokeLater(parent::revalidate);
                }
            }
        });
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension size = layoutSize(target, false);
        size.width -= getHgap() + 1;
        return size;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // Until the container has a width of its own, assume one row: a guess from an ancestor's width was
            // wrong while the panel was hidden, and the height it reserved stayed as a gap.
            int available = target.getWidth() > 0 ? target.getWidth() : Integer.MAX_VALUE;
            Insets insets = target.getInsets();
            int horizontalGaps = insets.left + insets.right + getHgap() * 2;
            int maxWidth = available - horizontalGaps;

            int width = 0;
            int height = 0;
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component child : target.getComponents()) {
                if (!child.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? child.getPreferredSize() : child.getMinimumSize();
                if (rowWidth + size.width > maxWidth && rowWidth > 0) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += getHgap();
                }
                rowWidth += size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            width = Math.max(width, rowWidth);
            height += rowHeight;
            return new Dimension(width + horizontalGaps, height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
}
