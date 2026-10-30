package org.de.rikr.ui;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Optional;

/**
 * Asks where to export the accepted mappings of a JAR pair, adding the .json extension and confirming before
 * replacing a file.
 */
final class MappingExportDialog {
    private MappingExportDialog() {
    }

    /**
     * @return the file to write, or empty if the user cancelled
     */
    static Optional<File> chooseDestination(Component parent, String sourceJar, String targetJar) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(stripJarSuffix(sourceJar) + "-to-" + stripJarSuffix(targetJar) + "-mappings.json"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }

        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith(".json")) {
            destination = new File(destination.getParentFile(), destination.getName() + ".json");
        }
        if (destination.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(
                parent,
                "Replace " + destination.getName() + "?",
                "Export mappings",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return Optional.empty();
            }
        }
        return Optional.of(destination);
    }

    private static String stripJarSuffix(String name) {
        return name.toLowerCase().endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }
}
