package org.de.rikr.ui;

import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.SearchResultItem;

import javax.swing.*;
import java.awt.*;

public class SearchResultItemRenderer extends JLabel implements ListCellRenderer<SearchResultItem> {
    private static String toHex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SearchResultItem> list, SearchResultItem value, int index, boolean isSelected, boolean cellHasFocus) {
        int indent = Math.max(0, value.getIndentLevel());
        setBorder(BorderFactory.createEmptyBorder(0, indent * 16, 0, 0));
        String primary = toHex(Theme.CONTENT_TEXT_COLOR);
        String secondary = toHex(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
        String html = "<html><span style='color:" + primary + "'>" + value.getText() + "</span> " +
            "<span style='color:" + secondary + "'>" + value.getClassNode().name + "</span></html>";
        setText(html);
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
