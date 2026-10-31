package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.resources.Images;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class NavigationPanel extends JPanel {
    private final Rikr controller;
    private final JButton projectButton;
    private final JButton searchButton;
    private final JButton mappingButton;
    private final JButton referencesButton;
    private final JButton bookmarksButton;
    private final JButton controlFlowButton;
    private SelectedTab selectedTab = SelectedTab.PROJECT;

    public NavigationPanel(
        Rikr controller,
        ActionListener projectAction,
        ActionListener searchAction,
        ActionListener mappingAction,
        ActionListener referencesAction,
        ActionListener bookmarksAction,
        ActionListener controlFlowAction
    ) {
        this.controller = controller;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BACKGROUND_COLOR);
        setBorder(null);

        projectButton = new JButton(Images.getImage("folder"));
        projectButton.setToolTipText("Project");
        projectButton.getAccessibleContext().setAccessibleName("Project");
        projectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        projectButton.setBorder(new FocusRingBorder());
        projectButton.addActionListener(projectAction);

        searchButton = new JButton(Images.getImage("search"));
        searchButton.setToolTipText("Search");
        searchButton.getAccessibleContext().setAccessibleName("Search");
        searchButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        searchButton.setBorder(new FocusRingBorder());
        searchButton.addActionListener(searchAction);

        mappingButton = new JButton(Images.getImage("mapping"));
        mappingButton.setToolTipText("Cross-JAR mappings");
        mappingButton.getAccessibleContext().setAccessibleName("Cross-JAR mappings");
        mappingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        mappingButton.setBorder(new FocusRingBorder());
        mappingButton.addActionListener(mappingAction);

        referencesButton = new JButton(Images.getImage("references"));
        referencesButton.setToolTipText("References and call hierarchy");
        referencesButton.getAccessibleContext().setAccessibleName("References and call hierarchy");
        referencesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        referencesButton.setBorder(new FocusRingBorder());
        referencesButton.addActionListener(referencesAction);

        bookmarksButton = new JButton(Images.getImage("bookmarks"));
        bookmarksButton.setToolTipText("Bookmarks");
        bookmarksButton.getAccessibleContext().setAccessibleName("Bookmarks");
        bookmarksButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        bookmarksButton.setBorder(new FocusRingBorder());
        bookmarksButton.addActionListener(bookmarksAction);

        controlFlowButton = new JButton(Images.getImage("flow"));
        controlFlowButton.setToolTipText("Entry points, call graphs and control flow");
        controlFlowButton.getAccessibleContext().setAccessibleName("Entry points, call graphs and control flow");
        controlFlowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlFlowButton.setBorder(new FocusRingBorder());
        controlFlowButton.addActionListener(controlFlowAction);

        add(Box.createRigidArea(new Dimension(36, 5)));
        add(projectButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(searchButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(mappingButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(referencesButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(controlFlowButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        // Bookmarks last: the workspaces above it follow the code; bookmarks are a list the person keeps.
        add(bookmarksButton);
        add(Box.createRigidArea(new Dimension(36, 5)));

        applyTheme();

        // React to theme changes
        SettingsManager.addListener(s -> {
            applyTheme();
        });
    }

    public void init() {
        projectButton.addActionListener(e -> {
            selectedTab = SelectedTab.PROJECT;
            applyTheme();
        });

        searchButton.addActionListener(e -> {
            selectedTab = SelectedTab.SEARCH;
            applyTheme();
        });

        mappingButton.addActionListener(e -> {
            selectedTab = SelectedTab.MAPPING;
            applyTheme();
        });

        referencesButton.addActionListener(e -> {
            selectedTab = SelectedTab.REFERENCES;
            applyTheme();
        });

        bookmarksButton.addActionListener(e -> {
            selectedTab = SelectedTab.BOOKMARKS;
            applyTheme();
        });

        controlFlowButton.addActionListener(e -> {
            selectedTab = SelectedTab.CONTROL_FLOW;
            applyTheme();
        });
    }

    public void selectProjectTab() {
        projectButton.doClick();
    }

    public void selectSearchTab() {
        searchButton.doClick();
    }

    public void selectMappingTab() {
        mappingButton.doClick();
    }

    public void selectReferencesTab() {
        referencesButton.doClick();
    }

    public void selectBookmarksTab() {
        bookmarksButton.doClick();
    }

    public void selectControlFlowTab() {
        controlFlowButton.doClick();
    }

    public String getSelectedTabId() {
        return selectedTab.name();
    }

    public void selectTab(String tabId) {
        try {
            switch (SelectedTab.valueOf(tabId)) {
                case PROJECT -> selectProjectTab();
                case SEARCH -> selectSearchTab();
                case MAPPING -> selectMappingTab();
                case REFERENCES -> selectReferencesTab();
                case BOOKMARKS -> selectBookmarksTab();
                case CONTROL_FLOW -> selectControlFlowTab();
                default -> selectProjectTab();
            }
        } catch (IllegalArgumentException exception) {
            selectProjectTab();
        }
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        style(projectButton, "folder", SelectedTab.PROJECT);
        style(searchButton, "search", SelectedTab.SEARCH);
        style(mappingButton, "mapping", SelectedTab.MAPPING);
        style(referencesButton, "references", SelectedTab.REFERENCES);
        style(bookmarksButton, "bookmarks", SelectedTab.BOOKMARKS);
        style(controlFlowButton, "flow", SelectedTab.CONTROL_FLOW);

        revalidate();
        repaint();
    }

    // The selected button's background is the accent colour in both themes; a white icon keeps 3:1 against it.
    private void style(JButton button, String icon, SelectedTab tab) {
        boolean selected = selectedTab == tab;
        button.setBackground(selected ? Theme.NAVBAR_BUTTON_SELECTED_COLOR : Theme.BACKGROUND_COLOR);
        button.setIcon(selected ? Images.getSelectedImage(icon) : Images.getThemedImage(icon));
        // The ring is white on the accent fill, so it shows on the selected button too.
        button.setBorder(new FocusRingBorder(selected));
    }

    private enum SelectedTab {
        PROJECT,
        SEARCH,
        MAPPING,
        REFERENCES,
        BOOKMARKS,
        CONTROL_FLOW
    }
}
