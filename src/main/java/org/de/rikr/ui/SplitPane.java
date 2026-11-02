package org.de.rikr.ui;

import javax.swing.*;

public class SplitPane extends JSplitPane {
    public SplitPane(int split, int dividerDragSize) {
        super(split);

        this.setUI(new DragAwareSplitPane(dividerDragSize, dividerDragSize / 2));
    }
}
