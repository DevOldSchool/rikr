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

        apply.addActionListener(e -> save(dialog, panel, frame, afterApply));
        ok.addActionListener(e -> {
            if (save(dialog, panel, frame, afterApply)) {
                dialog.dispose();
            }
        });
        cancel.addActionListener(e -> dialog.dispose());
        // Return applies and closes, as on every other macOS dialog.
        dialog.getRootPane().setDefaultButton(ok);

        JPanel content = new JPanel(new BorderLayout());
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // False, after saying why, when the settings can't be used; nothing is saved then.
    private static boolean save(JDialog dialog, SettingsPanel panel, JFrame frame, Runnable afterApply) {
        String problem = panel.problem();
        if (problem != null) {
            JOptionPane.showMessageDialog(dialog, problem, "Settings", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        Settings settings = panel.collectSettings();
        SettingsManager.applyAndSave(settings, frame);
        afterApply.run();
        return true;
    }
}
