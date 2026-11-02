package org.de.rikr.ui.action;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.format.MappingFormat;
import org.de.rikr.mapping.format.NameMappings;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Saves a JAR's renames as a mapping file. The file type chosen in the dialog picks the format.
 */
public final class RenameMappingExport {
    private RenameMappingExport() {
    }

    public static void export(Rikr controller, Component parent, String jar) {
        NameMappings mappings = controller.renames().nameMappings(jar);
        if (mappings.classes().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Nothing in " + jar + " has been renamed yet.",
                "Export Rename Mappings", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Rename Mappings");
        chooser.setAcceptAllFileFilterUsed(false);
        Map<FileFilter, MappingFormat> formats = new LinkedHashMap<>();
        for (MappingFormat format : MappingFormat.values()) {
            FileFilter filter = new FileNameExtensionFilter(
                format.displayName() + " (*." + format.extension() + ")", format.extension());
            formats.put(filter, format);
            chooser.addChoosableFileFilter(filter);
        }
        chooser.setFileFilter(formats.keySet().iterator().next());
        String baseName = jar.toLowerCase().endsWith(".jar") ? jar.substring(0, jar.length() - 4) : jar;
        chooser.setSelectedFile(new File(baseName + "-mappings"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        MappingFormat format = formats.get(chooser.getFileFilter());
        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith("." + format.extension())) {
            destination = new File(destination.getParentFile(), destination.getName() + "." + format.extension());
        }
        if (destination.exists() && JOptionPane.showConfirmDialog(parent, "Replace " + destination.getName() + "?",
            "Export Rename Mappings", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Files.writeString(destination.toPath(), format.write(mappings), StandardCharsets.UTF_8);
            controller.log("Exported " + mappings.classes().size() + " renamed classes of " + jar + " as "
                + format.displayName() + " to " + destination.getAbsolutePath());
        } catch (IOException exception) {
            controller.log("Export Rename Mappings failed: " + exception.getMessage());
            JOptionPane.showMessageDialog(parent, exception.getMessage(), "Export Rename Mappings",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
