package org.de.rikr.ui;

import org.de.rikr.settings.Settings;
import org.de.rikr.settings.SettingsManager;

import javax.swing.*;
import java.awt.*;

/**
 * The modal Settings window. Apply and OK save the settings and then run {@code afterApply}.
 */
final class SettingsDialog {
    private SettingsDialog() {
    }

    static void open(JFrame frame, Runnable afterApply) {
        JDialog dialog = new JDialog(frame, "Settings", true);
        SettingsPanel panel = new SettingsPanel(SettingsManager.getCurrent());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton apply = new JButton("Apply");
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);

        Runnable save = () -> {
            Settings settings = panel.collectSettings();
            SettingsManager.applyAndSave(settings, frame);
            afterApply.run();
        };
        apply.addActionListener(e -> save.run());
        ok.addActionListener(e -> {
            save.run();
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
