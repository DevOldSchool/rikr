package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class MenuBar extends JMenuBar {
    private final Rikr controller;
    private final JMenuItem groupBySuperclassItem;
    private final JMenuItem groupByInterfaceItem;
    private final JMenuItem removeGroupingItem;

    public MenuBar(Rikr controller, ActionListener openFileAction, ActionListener showSearchAction, ActionListener toggleLogAction) {
        this.controller = controller;

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

        // View menu
        JMenu viewMenu = new JMenu("View");
        groupBySuperclassItem = new JMenuItem("Group by Superclass");
        groupBySuperclassItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        groupByInterfaceItem = new JMenuItem("Group by Interface");
        groupByInterfaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        removeGroupingItem = new JMenuItem("Remove Grouping");
        removeGroupingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        viewMenu.add(groupBySuperclassItem);
        viewMenu.add(groupByInterfaceItem);
        viewMenu.add(removeGroupingItem);

        // Window menu
        JMenu windowMenu = new JMenu("Window");
        JMenuItem toggleLogItem = new JMenuItem("Toggle Log");
        toggleLogItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        toggleLogItem.addActionListener(toggleLogAction);
        windowMenu.add(toggleLogItem);

        add(fileMenu);
        add(editMenu);
        add(viewMenu);
        add(windowMenu);
    }

    public void init() {
        // Group nodes by superclass
        groupBySuperclassItem.addActionListener(e -> {
            controller.getUserInterface().getTreePanel().updateTree(controller.getJarClassesMap(), true, false);
        });

        // Group nodes by interface
        groupByInterfaceItem.addActionListener(e -> {
            controller.getUserInterface().getTreePanel().updateTree(controller.getJarClassesMap(), false, true);
        });

        // Remove grouping
        removeGroupingItem.addActionListener(e -> {
            controller.getUserInterface().getTreePanel().updateTree(controller.getJarClassesMap(), false, false);
        });
    }
}
