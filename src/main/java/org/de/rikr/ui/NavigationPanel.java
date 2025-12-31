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
    private boolean projectSelected = true;

    public NavigationPanel(Rikr controller, ActionListener projectAction, ActionListener searchAction) {
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

        add(Box.createRigidArea(new Dimension(36, 5)));
        add(projectButton);
        add(Box.createRigidArea(new Dimension(36, 5)));
        add(searchButton);
        add(Box.createRigidArea(new Dimension(36, 5)));

        applyTheme();

        // React to theme changes
        SettingsManager.addListener(s -> {
            applyTheme();
        });
    }

    public void init() {
        projectButton.addActionListener(e -> {
            projectSelected = true;
            applyTheme();
        });

        searchButton.addActionListener(e -> {
            projectSelected = false;
            applyTheme();
        });
    }

    public void selectProjectTab() {
        projectButton.doClick();
    }

    public void selectSearchTab() {
        searchButton.doClick();
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        if (projectSelected) {
            projectButton.setBackground(Theme.NAVBAR_BUTTON_SELECTED_COLOR);
            searchButton.setBackground(Theme.BACKGROUND_COLOR);
        } else {
            searchButton.setBackground(Theme.NAVBAR_BUTTON_SELECTED_COLOR);
            projectButton.setBackground(Theme.BACKGROUND_COLOR);
        }

        revalidate();
        repaint();
    }
}
