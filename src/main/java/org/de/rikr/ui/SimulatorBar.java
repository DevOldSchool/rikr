package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class SimulatorBar extends JPanel {
    private final Rikr controller;
    private final JButton stepForwardsButton;
    private final JButton stepBackwardsButton;
    private final JPanel topPanel;

    public SimulatorBar(Rikr controller, ActionListener closeListener) {
        this.controller = controller;

        setLayout(new BorderLayout());

        stepForwardsButton = new JButton("Step forwards");
        stepBackwardsButton = new JButton("Step backwards");
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(closeListener);

        // Left is earlier, as in debuggers and media players.
        topPanel = new JPanel();
        topPanel.add(stepBackwardsButton);
        topPanel.add(stepForwardsButton);
        topPanel.add(closeButton);
        topPanel.setBackground(Theme.BACKGROUND_COLOR);

        add(topPanel, BorderLayout.NORTH);

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        topPanel.setBackground(Theme.BACKGROUND_COLOR);

        revalidate();
        repaint();
    }

    public void init() {
        stepForwardsButton.addActionListener(e -> {
            stepForwardsButton.setEnabled(false);
            controller.getUserInterface().getMethodSimulatorPanel().stepForwards();
            stepForwardsButton.setEnabled(true);
        });

        stepBackwardsButton.addActionListener(e -> {
            stepBackwardsButton.setEnabled(false);
            controller.getUserInterface().getMethodSimulatorPanel().stepBackwards();
            stepBackwardsButton.setEnabled(true);
        });
    }
}
