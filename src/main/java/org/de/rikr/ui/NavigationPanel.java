package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class NavigationPanel extends JPanel {
    private final Rikr controller;
    private final JButton projectButton;
    private final JButton searchButton;

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

        projectButton.setBackground(Theme.NAVBAR_BUTTON_SELECTED_COLOR);
        searchButton.setBackground(Theme.BACKGROUND_COLOR);
    }

    public void init() {
        projectButton.addActionListener(e -> {
            projectButton.setBackground(Theme.NAVBAR_BUTTON_SELECTED_COLOR);
            searchButton.setBackground(Theme.BACKGROUND_COLOR);
        });

        searchButton.addActionListener(e -> {
            searchButton.setBackground(Theme.NAVBAR_BUTTON_SELECTED_COLOR);
            projectButton.setBackground(Theme.BACKGROUND_COLOR);
        });
    }
}
