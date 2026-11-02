package org.de.rikr.ui;

import org.de.rikr.settings.SettingsManager;

import javax.swing.*;

public class PanelUtil {
    public static JPanel createCorner() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBorder(null);
        panel.setBackground(SettingsManager.getScrollBarTrackColor());
        return panel;
    }
}
