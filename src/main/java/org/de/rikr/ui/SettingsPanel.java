package org.de.rikr.ui;

import org.de.rikr.settings.Settings;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemeProvider;
import org.de.rikr.theme.ThemeRegistry;

import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends JPanel {
    private final JComboBox<String> fontFamilyCombo;
    private final JSpinner fontSizeSpinner;
    private final JComboBox<ThemeProvider> themeCombo;

    public SettingsPanel() {
        this(SettingsManager.getCurrent());
    }

    public SettingsPanel(Settings settings) {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Theme.BACKGROUND_COLOR);

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.insets = new Insets(6, 6, 6, 6);

        add(label("Editor font"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontFamilyCombo = new JComboBox<>(families);
        fontFamilyCombo.setSelectedItem(settings.getFontFamily());
        add(fontFamilyCombo, gridBagConstraints);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        add(label("Font size"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getFontSize(), 8, 48, 1));
        add(fontSizeSpinner, gridBagConstraints);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        add(label("Theme"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        themeCombo = new JComboBox<>(ThemeRegistry.all());

        String id = (settings.getThemeId() != null && !settings.getThemeId().isEmpty()) ? settings.getThemeId() : "dark.flatij";
        ThemeProvider selected = ThemeRegistry.getById(id);
        if (selected != null) {
            themeCombo.setSelectedItem(selected);
        }
        add(themeCombo, gridBagConstraints);

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private JLabel label(String text) {
        // The look and feel's label colour is readable in every theme; the muted content colour is not.
        return new JLabel(text);
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        SettingsManager.updateComponentTree(this);

        revalidate();
        repaint();
    }

    public Settings collectSettings() {
        Settings settings = new Settings();
        settings.setFontFamily((String) fontFamilyCombo.getSelectedItem());
        settings.setFontSize((int) fontSizeSpinner.getValue());
        ThemeProvider provider = (ThemeProvider) themeCombo.getSelectedItem();
        if (provider != null) {
            settings.setThemeId(provider.id());
        }

        return settings;
    }
}
