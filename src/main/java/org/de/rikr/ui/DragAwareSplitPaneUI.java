package org.de.rikr.ui;

import org.de.rikr.theme.Theme;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

public class DragAwareSplitPaneUI extends BasicSplitPaneUI {
    private final int dragSize;
    private final int dragOffset;

    public DragAwareSplitPaneUI(int dragSize, int dragOffset) {
        this.dragSize = dragSize;
        this.dragOffset = dragOffset;
    }

    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        return new BasicSplitPaneDivider(this) {
            @Override
            public Dimension getPreferredSize() {
                Rectangle bounds = divider.getBounds();

                if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    bounds.x -= dragOffset;
                    bounds.width = dragSize;
                } else {
                    bounds.y -= dragOffset;
                    bounds.height = dragSize;
                }

                divider.setBounds(bounds);

                return new Dimension(1, 1);
            }

            @Override
            public void paint(Graphics g) {
                g.setColor(Theme.CONTENT_BORDER_COLOR);
                int width = getWidth();
                int height = getHeight();

                if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    int x = Math.max(0, dragOffset);
                    g.fillRect(x, 0, 1, height);
                } else {
                    int y = Math.max(0, dragOffset);
                    g.fillRect(0, y, width, 1);
                }
            }
        };
    }
}
