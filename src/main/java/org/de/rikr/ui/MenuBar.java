package org.de.rikr.ui;

import javax.swing.*;
import java.awt.event.ActionListener;

public class MenuBar {
    private final JMenuBar menuBar;
    private final JCheckBoxMenuItem logToggleMenuItem;

    public MenuBar(ActionListener openFileAction, ActionListener toggleLogAction) {
        menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openMenuItem = new JMenuItem("Open");
        openMenuItem.addActionListener(openFileAction);
        fileMenu.add(openMenuItem);
        menuBar.add(fileMenu);

        // Window Menu
        JMenu windowMenu = new JMenu("Window");
        logToggleMenuItem = new JCheckBoxMenuItem("Show Log");
        logToggleMenuItem.setSelected(true);
        logToggleMenuItem.addActionListener(toggleLogAction);
        windowMenu.add(logToggleMenuItem);
        menuBar.add(windowMenu);
    }

    public JMenuBar getMenuBar() {
        return menuBar;
    }

    public boolean isLogVisible() {
        return logToggleMenuItem.isSelected();
    }

    public void setLogVisible(boolean visible) {
        logToggleMenuItem.setSelected(visible);
    }
}
