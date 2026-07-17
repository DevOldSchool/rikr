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
    private SelectedTab selectedTab = SelectedTab.PROJECT;

    public NavigationPanel(
        Rikr controller,
        ActionListener projectAction,
        ActionListener searchAction,
        ActionListener mappingAction,
        ActionListener referencesAction
    ) {
        this.controller = controller;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.BACKGROUND_COLOR);
        setBorder(null);

        projectButton = new JButton(Images.getImage("folder"));
        projectButton.setToolTipText("Project");
        projectButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        projectButton.setBorderPainted(false);
        projectButton.addActionListener(projectAction);

        searchButton = new JButton(Images.getImage("search"));
        searchButton.setToolTipText("Search");
        searchButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        searchButton.setBorderPainted(false);
        searchButton.addActionListener(searchAction);

        mappingButton = new JButton("M");
        mappingButton.setToolTipText("Cross-JAR mappings");
        mappingButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        mappingButton.setBorderPainted(false);
        mappingButton.addActionListener(mappingAction);

        referencesButton = new JButton("R");
        referencesButton.setToolTipText("References and call hierarchy");
        referencesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        referencesButton.setBorderPainted(false);
        referencesButton.addActionListener(referencesAction);

        add(Box.createRigidArea(new Dimension(36, 5)));
        add(projectButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(searchButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(mappingButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(referencesButton);
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

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        projectButton.setBackground(
            selectedTab == SelectedTab.PROJECT ? Theme.NAVBAR_BUTTON_SELECTED_COLOR : Theme.BACKGROUND_COLOR
        );
        searchButton.setBackground(
            selectedTab == SelectedTab.SEARCH ? Theme.NAVBAR_BUTTON_SELECTED_COLOR : Theme.BACKGROUND_COLOR
        );
        mappingButton.setBackground(
            selectedTab == SelectedTab.MAPPING ? Theme.NAVBAR_BUTTON_SELECTED_COLOR : Theme.BACKGROUND_COLOR
        );
        referencesButton.setBackground(
            selectedTab == SelectedTab.REFERENCES ? Theme.NAVBAR_BUTTON_SELECTED_COLOR : Theme.BACKGROUND_COLOR
        );

        revalidate();
        repaint();
    }

    private enum SelectedTab {
        PROJECT,
        SEARCH,
        MAPPING,
        REFERENCES
    }
}
