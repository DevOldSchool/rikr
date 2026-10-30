package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

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
        "<html><div style='text-align:center'>No bookmarks yet.<br><br>Right-click a class, field or method "
            + "and choose Toggle Bookmark.</div></html>", SwingConstants.CENTER);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);

    public BookmarkWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        JLabel title = new JLabel("Bookmarks");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(event -> updateButtons());
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        content.add(new JScrollPane(list), "list");
        content.add(emptyLabel, "empty");
        add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        remove.addActionListener(event -> removeSelected());
        open.addActionListener(event -> openSelected());
        actions.add(remove);
        actions.add(open);
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

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        list.setBackground(Theme.BACKGROUND_COLOR);
        content.setBackground(Theme.BACKGROUND_COLOR);
        revalidate();
        repaint();
    }
}
