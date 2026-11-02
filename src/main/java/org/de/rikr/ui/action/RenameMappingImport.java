package org.de.rikr.ui.action;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.format.MappingFormat;
import org.de.rikr.mapping.format.MappingReader;
import org.de.rikr.mapping.format.NameMappings;
import org.de.rikr.project.RenameOperations;
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameResult;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/**
 * Applies a tiny v2, Enigma or ProGuard mapping file to a JAR as one undoable step, after showing what it will
 * rename. The format is recognised from the file's content.
 */
public final class RenameMappingImport {
    private static final String TITLE = "Import Rename Mappings";
    private static final int PROBLEMS_SHOWN = 15;

    private RenameMappingImport() {
    }

    public static void importInto(Rikr controller, Component parent, String jar) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(TITLE + " into " + jar);
        String[] extensions = Arrays.stream(MappingFormat.values()).map(MappingFormat::extension).toArray(String[]::new);
        chooser.setFileFilter(new FileNameExtensionFilter("Mapping files (tiny, mapping, txt)", extensions));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File source = chooser.getSelectedFile();

        RenameOperations.ImportPlan plan;
        try {
            NameMappings mappings = MappingReader.read(Files.readString(source.toPath(), StandardCharsets.UTF_8));
            plan = controller.renames().planImport(jar, mappings, source.getName());
        } catch (IOException exception) {
            fail(controller, parent, source.getName() + ": " + exception.getMessage());
            return;
        }

        if (!plan.problems().isEmpty()) {
            List<String> shown = plan.problems().stream().limit(PROBLEMS_SHOWN).toList();
            String more = plan.problems().size() > PROBLEMS_SHOWN
                ? "\n...and " + (plan.problems().size() - PROBLEMS_SHOWN) + " more."
                : "";
            fail(controller, parent, source.getName() + " cannot be applied to " + jar + ", so nothing was renamed:\n\n"
                + String.join("\n", shown) + more);
            return;
        }
        RenameBatch batch = plan.batch();
        String skipped = plan.skipped() == 0 ? "" : String.format(
            "%n%n%d entries name classes or members that are not in %s and will be skipped.", plan.skipped(), jar);
        if (batch.size() == 0) {
            JOptionPane.showMessageDialog(parent, source.getName() + " renames nothing in " + jar + "." + skipped,
                TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String message = String.format(
            "Import %s into %s?%n%nClasses renamed: %d%nFields renamed: %d%nMethods renamed: %d (including overrides)%s"
                + "%n%nUndo reverts the whole import.",
            source.getName(), jar, batch.classes().size(), batch.fields().size(), batch.methods().size(), skipped);
        if (JOptionPane.showConfirmDialog(parent, message, TITLE, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            RenameResult result = controller.renames().applyImport(plan);
            controller.log(String.format("Imported %s into %s: renamed %d classes, %d fields and %d methods, "
                    + "changing %d references in %d classes.", source.getName(), jar, batch.classes().size(),
                batch.fields().size(), batch.methods().size(), result.referenceCount(), result.fileCount()));
            controller.getUserInterface().refreshCode();
        } catch (IllegalArgumentException exception) {
            fail(controller, parent, exception.getMessage());
        }
    }

    private static void fail(Rikr controller, Component parent, String message) {
        controller.log(TITLE + " failed: " + message);
        JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
