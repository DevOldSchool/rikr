package org.de.rikr.ui;

import org.de.rikr.settings.DecompilerCacheSettings;
import org.de.rikr.settings.Settings;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.theme.ThemeProvider;
import org.de.rikr.theme.ThemeRegistry;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsPanel extends JPanel {
    private final JComboBox<String> fontFamilyCombo;
    private final JSpinner fontSizeSpinner;
    private final JComboBox<ThemeProvider> themeCombo;
    private final JTextField cacheFolderField;
    private final JSpinner cacheSizeSpinner;

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
        fontFamilyCombo.getAccessibleContext().setAccessibleName("Editor font");
        add(fontFamilyCombo, gridBagConstraints);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        add(label("Font size"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        fontSizeSpinner = new JSpinner(new SpinnerNumberModel(settings.getFontSize(), 8, 48, 1));
        fontSizeSpinner.getAccessibleContext().setAccessibleName("Font size");
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
        themeCombo.getAccessibleContext().setAccessibleName("Theme");
        add(themeCombo, gridBagConstraints);

        DecompilerCacheSettings cache = settings.getDecompilerCache();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new Insets(14, 6, 2, 6);
        JLabel cacheHeading = label("Decompiled Java cache");
        cacheHeading.setFont(cacheHeading.getFont().deriveFont(Font.BOLD));
        add(cacheHeading, gridBagConstraints);

        gridBagConstraints.gridy++;
        gridBagConstraints.insets = new Insets(0, 6, 6, 6);
        // A width makes the HTML wrap instead of widening the window.
        JLabel cacheHint = new JLabel("<html><div style='width: 400px'>Decompiled Java is kept on disk, so a class "
            + "opens at once the next time, in any project. Past the size limit, the classes opened least recently "
            + "are deleted. A new folder starts empty, and the old one is left as it is.</div></html>");
        cacheHint.putClientProperty("FlatLaf.styleClass", "small");
        add(cacheHint, gridBagConstraints);
        gridBagConstraints.gridwidth = 1;
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.insets = new Insets(6, 6, 6, 6);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        add(label("Folder"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        cacheFolderField = new JTextField(cache.directory(), 28);
        cacheFolderField.putClientProperty("JTextField.placeholderText",
            DecompilerCacheSettings.DEFAULT_DIRECTORY.toString());
        cacheFolderField.getAccessibleContext().setAccessibleName("Cache folder");
        cacheFolderField.setToolTipText("Leave empty for " + DecompilerCacheSettings.DEFAULT_DIRECTORY);
        JButton chooseFolder = new JButton("Choose...");
        chooseFolder.addActionListener(event -> chooseCacheFolder());
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.setOpaque(false);
        folderRow.add(cacheFolderField, BorderLayout.CENTER);
        folderRow.add(chooseFolder, BorderLayout.EAST);
        add(folderRow, gridBagConstraints);

        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy++;
        add(label("Size limit"), gridBagConstraints);
        gridBagConstraints.gridx = 1;
        cacheSizeSpinner = new JSpinner(new SpinnerNumberModel(cache.maxSizeMb(),
            DecompilerCacheSettings.MIN_MAX_SIZE_MB, DecompilerCacheSettings.MAX_MAX_SIZE_MB, 50));
        cacheSizeSpinner.getAccessibleContext().setAccessibleName("Cache size limit in megabytes");
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sizeRow.setOpaque(false);
        sizeRow.add(cacheSizeSpinner);
        sizeRow.add(Box.createHorizontalStrut(6));
        sizeRow.add(label("MB"));
        add(sizeRow, gridBagConstraints);

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

    private void chooseCacheFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose the Cache Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = cacheFolderField.getText().strip();
        File start = current.isEmpty() ? DecompilerCacheSettings.DEFAULT_DIRECTORY.toFile() : new File(current);
        // The chooser opens in the nearest folder that exists.
        while (start != null && !start.isDirectory()) {
            start = start.getParentFile();
        }
        chooser.setCurrentDirectory(start);
        if (chooser.showDialog(this, "Choose") == JFileChooser.APPROVE_OPTION) {
            cacheFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Why the settings can't be used, to show before saving them, or null. Creates the cache folder if it is
     * missing, since that is the way to know it can be.
     */
    public String problem() {
        String folder = cacheFolderField.getText().strip();
        if (folder.isEmpty()) {
            return null;
        }
        Path path;
        try {
            path = Paths.get(folder);
        } catch (InvalidPathException invalid) {
            return "\"" + folder + "\" isn't a folder path. Choose a folder for the decompiled Java cache, or leave "
                + "it empty for " + DecompilerCacheSettings.DEFAULT_DIRECTORY + ".";
        }
        if (!path.isAbsolute()) {
            return "The cache folder needs a full path, such as " + DecompilerCacheSettings.DEFAULT_DIRECTORY
                + ". Choose a folder, or leave it empty for the default.";
        }
        try {
            Files.createDirectories(path);
        } catch (IOException | SecurityException unusable) {
            return "Rikr can't create " + path + " for the decompiled Java cache. Choose another folder, or leave it "
                + "empty for " + DecompilerCacheSettings.DEFAULT_DIRECTORY + ".";
        }
        if (!Files.isWritable(path)) {
            return "Rikr can't write to " + path + ". Choose another folder for the decompiled Java cache, or leave "
                + "it empty for " + DecompilerCacheSettings.DEFAULT_DIRECTORY + ".";
        }
        return null;
    }

    public Settings collectSettings() {
        Settings settings = new Settings();
        settings.setFontFamily((String) fontFamilyCombo.getSelectedItem());
        settings.setFontSize((int) fontSizeSpinner.getValue());
        ThemeProvider provider = (ThemeProvider) themeCombo.getSelectedItem();
        if (provider != null) {
            settings.setThemeId(provider.id());
        }
        settings.setDecompilerCache(new DecompilerCacheSettings(cacheFolderField.getText(),
            (int) cacheSizeSpinner.getValue()));

        return settings;
    }
}
