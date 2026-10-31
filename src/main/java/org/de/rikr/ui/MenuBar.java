package org.de.rikr.ui;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * The application menus. Shortcuts follow macOS conventions: Cmd+S saves the project, Cmd+G finds the next match.
 * Actions that change files outside the project, such as overwriting a JAR, have no shortcut.
 */
public class MenuBar extends JMenuBar {

    /**
     * What each menu item does.
     */
    public record Actions(
        Runnable openFiles,
        Runnable openProject,
        Runnable saveProject,
        Runnable saveProjectAs,
        Runnable exportClass,
        Runnable exportJar,
        Runnable overwriteOriginalJar,
        Runnable undoRename,
        Runnable find,
        Runnable findNext,
        Runnable findPrevious,
        Runnable goTo,
        Runnable back,
        Runnable forward,
        Runnable showProject,
        Runnable showSearch,
        Runnable showMapping,
        Runnable showReferences,
        Runnable showBookmarks,
        Runnable showFlow,
        Runnable toggleLog,
        Runnable openSettings
    ) {
    }

    private static final int MENU = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final JCheckBoxMenuItem splitAsmJavaItem = new JCheckBoxMenuItem("Split Bytecode and Java");
    private final Rikr controller;
    private final JMenuItem groupBySuperclassItem = new JMenuItem("Group by Superclass");
    private final JMenuItem groupByInterfaceItem = new JMenuItem("Group by Interface");
    private final JMenuItem removeGroupingItem = new JMenuItem("Remove Grouping");
    private final JCheckBoxMenuItem annotationItem = new JCheckBoxMenuItem("Strip Annotations When Loading");

    public MenuBar(Rikr controller, Actions actions) {
        this.controller = controller;

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(item("Open JAR or Class...", KeyEvent.VK_O, MENU, actions.openFiles()));
        fileMenu.add(item("Open Project...", KeyEvent.VK_O, MENU | KeyEvent.SHIFT_DOWN_MASK, actions.openProject()));
        fileMenu.addSeparator();
        fileMenu.add(item("Save Project", KeyEvent.VK_S, MENU, actions.saveProject()));
        fileMenu.add(item("Save Project As...", KeyEvent.VK_S, MENU | KeyEvent.SHIFT_DOWN_MASK,
            actions.saveProjectAs()));
        fileMenu.addSeparator();
        fileMenu.add(item("Export Class...", KeyEvent.VK_S, MENU | KeyEvent.CTRL_DOWN_MASK, actions.exportClass()));
        fileMenu.add(item("Export JAR...", KeyEvent.VK_S, MENU | KeyEvent.ALT_DOWN_MASK, actions.exportJar()));
        fileMenu.addSeparator();
        // Rewrites a file the user loaded, so it is never one keystroke away; it confirms first.
        fileMenu.add(item("Overwrite Original JAR...", 0, 0, actions.overwriteOriginalJar()));

        JMenu editMenu = new JMenu("Edit");
        editMenu.add(item("Undo Rename", KeyEvent.VK_Z, MENU, actions.undoRename()));
        editMenu.addSeparator();
        editMenu.add(item("Find in Class...", KeyEvent.VK_F, MENU, actions.find()));
        editMenu.add(item("Find Next", KeyEvent.VK_G, MENU, actions.findNext()));
        editMenu.add(item("Find Previous", KeyEvent.VK_G, MENU | KeyEvent.SHIFT_DOWN_MASK, actions.findPrevious()));
        editMenu.addSeparator();
        annotationItem.setToolTipText("Leave annotations out of JARs and classes loaded from now on");
        editMenu.add(annotationItem);
        // On macOS, Settings is in the application menu.
        if (!SystemInfo.isMacOS) {
            editMenu.addSeparator();
            editMenu.add(item("Settings...", KeyEvent.VK_COMMA, MENU, actions.openSettings()));
        }

        JMenu navigateMenu = new JMenu("Navigate");
        navigateMenu.add(item("Go to Class or Member...", KeyEvent.VK_K, MENU, actions.goTo()));
        navigateMenu.addSeparator();
        navigateMenu.add(item("Back", KeyEvent.VK_OPEN_BRACKET, MENU, actions.back()));
        navigateMenu.add(item("Forward", KeyEvent.VK_CLOSE_BRACKET, MENU, actions.forward()));

        JMenu viewMenu = new JMenu("View");
        int view = MENU | KeyEvent.SHIFT_DOWN_MASK;
        viewMenu.add(item("Project", KeyEvent.VK_E, view, actions.showProject()));
        viewMenu.add(item("Search", KeyEvent.VK_F, view, actions.showSearch()));
        viewMenu.add(item("Cross-JAR Mappings", KeyEvent.VK_M, view, actions.showMapping()));
        viewMenu.add(item("References and Call Hierarchy", KeyEvent.VK_R, view, actions.showReferences()));
        viewMenu.add(item("Entry Points and Flow", KeyEvent.VK_C, view, actions.showFlow()));
        viewMenu.add(item("Bookmarks", KeyEvent.VK_B, view, actions.showBookmarks()));
        viewMenu.addSeparator();
        viewMenu.add(groupBySuperclassItem);
        viewMenu.add(groupByInterfaceItem);
        viewMenu.add(removeGroupingItem);
        viewMenu.addSeparator();
        splitAsmJavaItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_J, MENU | KeyEvent.SHIFT_DOWN_MASK));
        viewMenu.add(splitAsmJavaItem);

        JMenu windowMenu = new JMenu("Window");
        windowMenu.add(item("Show or Hide Log", KeyEvent.VK_L, MENU, actions.toggleLog()));

        add(fileMenu);
        add(editMenu);
        add(navigateMenu);
        add(viewMenu);
        add(windowMenu);
    }

    private static JMenuItem item(String text, int key, int modifiers, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (key != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        }
        item.addActionListener(event -> action.run());
        return item;
    }

    public void init() {
        groupBySuperclassItem.addActionListener(e ->
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), true, false));
        groupByInterfaceItem.addActionListener(e ->
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), false, true));
        removeGroupingItem.addActionListener(e ->
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), false, false));
        annotationItem.addActionListener(e -> controller.jars().setStripAnnotations(annotationItem.isSelected()));
    }

    public JCheckBoxMenuItem getSplitAsmJavaItem() {
        return splitAsmJavaItem;
    }
}
