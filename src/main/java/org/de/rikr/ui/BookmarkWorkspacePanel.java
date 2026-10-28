package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.service.ProjectBookmark;
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

    public BookmarkWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Bookmarks"));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton remove = new JButton("Remove");
        JButton open = new JButton("Open");
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
    }

    public void refresh() {
        ProjectBookmark selected = list.getSelectedValue();
        model.clear();
        controller.bookmarks().all().forEach(model::addElement);
        if (selected != null) {
            list.setSelectedValue(selected, true);
        }
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
        revalidate();
        repaint();
    }
}
