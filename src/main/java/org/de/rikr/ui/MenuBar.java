package org.de.rikr.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class MenuBar extends JMenuBar {
    public MenuBar(ActionListener openFileAction, ActionListener showSearchAction, ActionListener toggleLogAction) {
        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openFileItem = new JMenuItem("Open");
        openFileItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openFileItem.addActionListener(openFileAction);
        fileMenu.add(openFileItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        JMenuItem searchItem = new JMenuItem("Search");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        searchItem.addActionListener(showSearchAction);
        editMenu.add(searchItem);

        // Window menu
        JMenu windowMenu = new JMenu("Window");
        JMenuItem toggleLogItem = new JMenuItem("Toggle Log");
        toggleLogItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        toggleLogItem.addActionListener(toggleLogAction);
        windowMenu.add(toggleLogItem);

        add(fileMenu);
        add(editMenu);
        add(windowMenu);
    }
}
