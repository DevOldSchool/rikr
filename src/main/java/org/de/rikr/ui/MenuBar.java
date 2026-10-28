package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public class MenuBar extends JMenuBar {
    private final JCheckBoxMenuItem splitAsmJavaItem;
    private final Rikr controller;
    private final JMenuItem groupBySuperclassItem;
    private final JMenuItem groupByInterfaceItem;
    private final JMenuItem removeGroupingItem;
    private final JCheckBoxMenuItem annotationItem;

    public MenuBar(
        Rikr controller,
        ActionListener openFileAction,
        ActionListener openProjectAction,
        ActionListener saveProjectAction,
        ActionListener saveProjectAsAction,
        ActionListener showSearchAction,
        ActionListener undoRenameAction,
        ActionListener showProjectAction,
        ActionListener showGlobalSearchAction,
        ActionListener showMappingAction,
        ActionListener showReferencesAction,
        ActionListener showBookmarksAction,
        ActionListener toggleLogAction,
        ActionListener saveSelectedClassAction,
        ActionListener saveSelectedJarAction,
        ActionListener overwriteOriginalJarAction,
        ActionListener openSettingsAction
    ) {
        this.controller = controller;

        // File menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openFileItem = new JMenuItem("Open");
        openFileItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        openFileItem.addActionListener(openFileAction);
        fileMenu.add(openFileItem);
        fileMenu.addSeparator();

        JMenuItem openProjectItem = new JMenuItem("Open Project...");
        openProjectItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_O,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK
        ));
        openProjectItem.addActionListener(openProjectAction);
        fileMenu.add(openProjectItem);

        JMenuItem saveProjectItem = new JMenuItem("Save Project");
        saveProjectItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_S,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK
        ));
        saveProjectItem.addActionListener(saveProjectAction);
        fileMenu.add(saveProjectItem);

        JMenuItem saveProjectAsItem = new JMenuItem("Save Project As...");
        saveProjectAsItem.addActionListener(saveProjectAsAction);
        fileMenu.add(saveProjectAsItem);
        fileMenu.addSeparator();

        JMenuItem saveSelectedClassItem = new JMenuItem("Save Selected Class");
        saveSelectedClassItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.CTRL_DOWN_MASK));
        saveSelectedClassItem.addActionListener(saveSelectedClassAction);

        JMenuItem saveSelectedJarItem = new JMenuItem("Save Selected JAR");
        saveSelectedJarItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.ALT_DOWN_MASK));
        saveSelectedJarItem.addActionListener(saveSelectedJarAction);

        JMenuItem overwriteOriginalJarItem = new JMenuItem("Overwrite Original JAR");
        overwriteOriginalJarItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        overwriteOriginalJarItem.addActionListener(overwriteOriginalJarAction);

        fileMenu.add(saveSelectedClassItem);
        fileMenu.add(saveSelectedJarItem);
        fileMenu.addSeparator();
        fileMenu.add(overwriteOriginalJarItem);

        // Edit menu
        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoRenameItem = new JMenuItem("Undo Rename");
        undoRenameItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_Z,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
        ));
        undoRenameItem.addActionListener(undoRenameAction);
        editMenu.add(undoRenameItem);
        editMenu.addSeparator();
        JMenuItem searchItem = new JMenuItem("Search");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        searchItem.addActionListener(showSearchAction);
        editMenu.add(searchItem);

        annotationItem = new JCheckBoxMenuItem("Strip annotations");
        annotationItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        annotationItem.addActionListener(showSearchAction);
        editMenu.add(annotationItem);

        // Settings (non-macOS only; on macOS it appears under the application menu as Preferences)
        if (!SystemInfo.isMacOS) {
            JMenuItem settingsItem = new JMenuItem("Settings");
            settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
            settingsItem.addActionListener(openSettingsAction);
            editMenu.addSeparator();
            editMenu.add(settingsItem);
        }

        // View menu
        JMenu viewMenu = new JMenu("View");
        JMenuItem projectSearchItem = new JMenuItem("Project");
        projectSearchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        projectSearchItem.addActionListener(showProjectAction);
        viewMenu.add(projectSearchItem);

        JMenuItem globalSearchItem = new JMenuItem("Global Search");
        globalSearchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK));
        globalSearchItem.addActionListener(showGlobalSearchAction);
        viewMenu.add(globalSearchItem);

        JMenuItem mappingItem = new JMenuItem("Cross-JAR Mappings");
        mappingItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_M,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK
        ));
        mappingItem.addActionListener(showMappingAction);
        viewMenu.add(mappingItem);

        JMenuItem referencesItem = new JMenuItem("References and Call Hierarchy");
        referencesItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_R,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK
        ));
        referencesItem.addActionListener(showReferencesAction);
        viewMenu.add(referencesItem);

        JMenuItem bookmarksItem = new JMenuItem("Bookmarks");
        bookmarksItem.setAccelerator(KeyStroke.getKeyStroke(
            KeyEvent.VK_B,
            Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | KeyEvent.SHIFT_DOWN_MASK
        ));
        bookmarksItem.addActionListener(showBookmarksAction);
        viewMenu.add(bookmarksItem);

        viewMenu.addSeparator();

        groupBySuperclassItem = new JMenuItem("Group by Superclass");
        groupBySuperclassItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        groupByInterfaceItem = new JMenuItem("Group by Interface");
        groupByInterfaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        removeGroupingItem = new JMenuItem("Remove Grouping");
        removeGroupingItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

        viewMenu.add(groupBySuperclassItem);
        viewMenu.add(groupByInterfaceItem);
        viewMenu.add(removeGroupingItem);

        viewMenu.addSeparator();
        splitAsmJavaItem = new JCheckBoxMenuItem("Split ASM and Java");
        viewMenu.add(splitAsmJavaItem);

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
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), true, false);
        });

        // Group nodes by interface
        groupByInterfaceItem.addActionListener(e -> {
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), false, true);
        });

        // Remove grouping
        removeGroupingItem.addActionListener(e -> {
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), false, false);
        });

        annotationItem.addActionListener(e -> {
            controller.jars().setStripAnnotations(annotationItem.isSelected());
        });
    }

    public JCheckBoxMenuItem getSplitAsmJavaItem() {
        return splitAsmJavaItem;
    }
}
