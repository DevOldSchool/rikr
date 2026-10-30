package org.de.rikr.ui;

import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The bar along the bottom of the window: the latest message, whether work is running in the background, and what
 * is loaded. Click the message, or Log, to see every message.
 */
public class StatusBar extends JPanel {
    private final JLabel message = new JLabel(" ");
    private final JProgressBar busy = new JProgressBar();
    private final JLabel busyLabel = new JLabel("Working…");
    private final JLabel summary = new JLabel("No JARs loaded");
    private final JButton logButton = new JButton("Log");

    public StatusBar(Runnable toggleLog) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 6));
        message.setToolTipText("Show the log");
        message.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        message.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                toggleLog.run();
            }
        });
        busy.setIndeterminate(true);
        busy.setPreferredSize(new Dimension(80, 6));
        busy.getAccessibleContext().setAccessibleName("Working in the background");
        logButton.setToolTipText("Show or hide the log (Cmd+L)");
        logButton.addActionListener(event -> toggleLog.run());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(busyLabel);
        right.add(busy);
        right.add(summary);
        right.add(logButton);
        add(message, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
        setBusy(false);
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    /**
     * Show the latest log message, on one line.
     */
    public void setMessage(String text) {
        String line = text.lines().findFirst().orElse("");
        message.setText(line);
        message.setToolTipText(text.length() > line.length() ? text + "\n\nClick to show the log" : "Show the log");
    }

    public void setBusy(boolean working) {
        busy.setVisible(working);
        busyLabel.setVisible(working);
    }

    public void setSummary(String text) {
        summary.setText(text);
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.CONTENT_BORDER_COLOR),
            BorderFactory.createEmptyBorder(3, 10, 3, 6)));
    }
}
