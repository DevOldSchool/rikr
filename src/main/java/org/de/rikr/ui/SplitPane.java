package org.de.rikr.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

public class SplitPane extends JSplitPane {
    private final int dividerDragSize;
    private final int dividerDragOffset;

    public SplitPane(int split, int dividerDragSize) {
        super(split);
        this.dividerDragSize = dividerDragSize;
        dividerDragOffset = dividerDragSize / 2;
    }

    @Override
    public void layout() {
        super.layout();

        // increase divider width or height
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) getUI()).getDivider();
        Rectangle bounds = divider.getBounds();

        if (orientation == HORIZONTAL_SPLIT) {
            bounds.x -= dividerDragOffset;
            bounds.width = dividerDragSize;
        } else {
            bounds.y -= dividerDragOffset;
            bounds.height = dividerDragSize;
        }

        divider.setBounds(bounds);
    }
}
