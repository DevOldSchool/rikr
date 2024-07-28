package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class SimulatorBar extends JPanel {
    private final Rikr controller;
    private final JButton stepForwardsButton;
    private final JButton stepBackwardsButton;

    public SimulatorBar(Rikr controller, ActionListener closeListener) {
        this.controller = controller;

        setLayout(new BorderLayout());

        stepForwardsButton = new JButton("Step forwards");
        stepForwardsButton.setBorderPainted(false);
        stepBackwardsButton = new JButton("Step backwards");
        stepBackwardsButton.setBorderPainted(false);
        JButton closeButton = new JButton("Close");
        closeButton.setBorderPainted(false);
        closeButton.addActionListener(closeListener);

        JPanel topPanel = new JPanel();
        topPanel.add(stepForwardsButton);
        topPanel.add(stepBackwardsButton);
        topPanel.add(closeButton);
        topPanel.setBackground(Theme.BACKGROUND_COLOR);

        add(topPanel, BorderLayout.NORTH);
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
