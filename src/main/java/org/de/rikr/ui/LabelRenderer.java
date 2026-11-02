package org.de.rikr.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

/**
 * A list renderer that shows each value as the text {@code label} gives it.
 */
class LabelRenderer<T> extends DefaultListCellRenderer {
    private final Class<T> type;
    private final Function<T, String> label;

    LabelRenderer(Class<T> type, Function<T, String> label) {
        this.type = type;
        this.label = label;
    }

    @Override
    public Component getListCellRendererComponent(
        JList<?> list,
        Object value,
        int index,
        boolean isSelected,
        boolean cellHasFocus
    ) {
        JLabel component = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (type.isInstance(value)) {
            component.setText(label.apply(type.cast(value)));
        }
        return component;
    }
}
