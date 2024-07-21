package org.de.rikr.ui;

import org.de.rikr.ui.model.SearchResultItem;

import javax.swing.*;
import java.awt.*;

public class SearchResultItemRenderer extends JLabel implements ListCellRenderer<SearchResultItem> {
    @Override
    public Component getListCellRendererComponent(JList<? extends SearchResultItem> list, SearchResultItem value, int index, boolean isSelected, boolean cellHasFocus) {
        String text = "<html>" + value.getText() + " <font color='gray'>" + value.getClassNode().name + "</font></html>";

        setText(text);
        setIcon(value.getIcon());

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        setEnabled(list.isEnabled());
        setFont(list.getFont());
        setOpaque(true);

        return this;
    }
}
