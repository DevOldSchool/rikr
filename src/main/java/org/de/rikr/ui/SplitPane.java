package org.de.rikr.ui;

import javax.swing.*;

public class SplitPane extends JSplitPane {
    public SplitPane(int split, int dividerDragSize) {
        super(split);

        this.setUI(new DragAwareSplitPaneUI(dividerDragSize, dividerDragSize / 2));
    }
}
