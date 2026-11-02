package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class BookmarkWorkspacePanel extends JPanel {
    private final Rikr controller;
    private final DefaultListModel<ProjectBookmark> model = new DefaultListModel<>();
    private final JList<ProjectBookmark> list = new JList<>(model);
    private final JButton remove = new JButton("Remove");
    private final JButton open = new JButton("Open");
    private final JLabel emptyLabel = new JLabel(
        "<html><div style='text-align:center'>No bookmarks yet.<br><br>Select a class, field or method and "
            + "press F3, or right-click it and choose Add Bookmark.</div></html>", SwingConstants.CENTER);
    private final CardLayout cards = new CardLayout();
    private ClassNode openClass;
    // Remove and Open have nothing to act on with no bookmarks, so they are hidden rather than disabled.
    private final JPanel actions = new JPanel(new BorderLayout());
    private final JPanel content = new JPanel(cards);

    public BookmarkWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        JLabel title = new JLabel("Bookmarks");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Bookmarks");
        // Bookmarks in the class that is open are shown in bold, so the ones at hand stand out.
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected,
                                                          boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focused);
                if (!(value instanceof ProjectBookmark bookmark)) {
                    return label;
                }
                boolean inOpenClass = openClass != null && bookmark.className().equals(openClass.name)
                    && controller.jars().jarOf(openClass).map(bookmark.jarName()::equals).orElse(false);
                String name = escape(bookmark.label());
                label.setText("<html>" + (inOpenClass ? "<b>" + name + "</b>" : name) + " <span style='color:"
                    + SecondaryText.color(owner, selected) + "'>" + escape(bookmark.jarName()) + "</span></html>");
                // The same icons as the project tree, so a class, field and method look as they do there.
                String kind = switch (bookmark.type()) {
                    case CLASS -> "Class";
                    case FIELD -> "Field";
                    case METHOD -> "Method";
                };
                label.setIcon(ClassNodeImages.getImage(switch (bookmark.type()) {
                    case CLASS -> "class";
                    case FIELD -> "field_default";
                    case METHOD -> "method_default";
                }));
                label.setToolTipText(kind + (inOpenClass ? ", in the open class" : ""));
                label.getAccessibleContext().setAccessibleName(kind + " " + bookmark.label() + " in "
                    + bookmark.jarName() + (inOpenClass ? ", in the open class" : ""));
                return label;
            }
        });
        list.addListSelectionListener(event -> updateButtons());
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        content.add(new JScrollPane(list), "list");
        content.add(emptyLabel, "empty");
        add(content, BorderLayout.CENTER);

        remove.addActionListener(event -> removeSelected());
        open.addActionListener(event -> openSelected());
        // Remove stands apart from Open, so the button used most is never next to the one that deletes.
        actions.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        actions.add(remove, BorderLayout.WEST);
        actions.add(open, BorderLayout.EAST);
        remove.setToolTipText("Remove the selected bookmark (Delete)");
        open.setToolTipText("Open the selected bookmark (Enter)");
        add(actions, BorderLayout.SOUTH);

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelected();
                } else if (event.getKeyCode() == KeyEvent.VK_DELETE || event.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeSelected();
                }
            }
        });
        SettingsManager.addListener(settings -> applyTheme());
        applyTheme();
        showListOrEmptyState();
    }

    public void classOpened(ClassNode classNode) {
        openClass = classNode;
        list.repaint();
    }

    public void refresh() {
        ProjectBookmark selected = list.getSelectedValue();
        model.clear();
        controller.bookmarks().all().forEach(model::addElement);
        if (selected != null) {
            list.setSelectedValue(selected, true);
        }
        showListOrEmptyState();
    }

    private void showListOrEmptyState() {
        cards.show(content, model.isEmpty() ? "empty" : "list");
        actions.setVisible(!model.isEmpty());
        updateButtons();
    }

    // Remove and Open act on the selected bookmark, so they are only enabled while one is selected.
    private void updateButtons() {
        boolean selected = list.getSelectedValue() != null;
        remove.setEnabled(selected);
        open.setEnabled(selected);
    }

    private void openSelected() {
        ProjectBookmark bookmark = list.getSelectedValue();
        if (bookmark != null && !controller.openBookmark(bookmark)) {
            JOptionPane.showMessageDialog(
                this,
                "The bookmarked element is no longer available.",
                "Open Bookmark",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void removeSelected() {
        ProjectBookmark bookmark = list.getSelectedValue();
        if (bookmark != null) {
            controller.bookmarks().remove(bookmark);
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        list.setBackground(Theme.BACKGROUND_COLOR);
        content.setBackground(Theme.BACKGROUND_COLOR);
        revalidate();
        repaint();
    }
}
