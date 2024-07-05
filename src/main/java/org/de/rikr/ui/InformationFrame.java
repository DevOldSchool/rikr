package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class InformationFrame extends JFrame {

    public InformationFrame(Rikr controller, Map<String, String> informationMap) {
        setTitle("Information");

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int rowCount = 0;
        for (Map.Entry<String, String> entry : informationMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            addInfo(panel, gbc, key + ":", value, rowCount);
            rowCount++;
        }

        add(panel);
        pack();

        setLocationRelativeTo(controller.getUserInterface().getFrame());
        setResizable(false);
        setVisible(true);
    }

    private void addInfo(JPanel panel, GridBagConstraints gbc, String labelText, String valueText, int y) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Arial", Font.BOLD, 14));
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(label, gbc);

        JLabel value = new JLabel(valueText);
        value.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(value, gbc);
    }
}
