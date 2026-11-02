package org.de.rikr.ui.action;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.MemberMatches;
import org.de.rikr.project.RenameOperations;
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gives the classes, fields and methods of one JAR the names someone gave their matches in another JAR, as one
 * undoable step, after showing what it will rename. Members are matched in the background from the accepted
 * class mappings.
 */
public final class NameTransferAction {
    private static final String TITLE = "Carry Names";
    private static final int PROBLEMS_SHOWN = 10;

    private NameTransferAction() {
    }

    /**
     * @param onFinished told on the event thread what happened, for a status line
     */
    public static void carry(Rikr controller, Component parent, String sourceJar, String targetJar,
        Consumer<String> onFinished) {
        controller.runInBackground(
            "Matching members of " + sourceJar + " and " + targetJar,
            controller.mappings().matchMembersLater(sourceJar, targetJar),
            matches -> confirmAndApply(controller, parent, sourceJar, targetJar, matches, onFinished),
            exception -> {
                String message = TITLE + " failed: " + exception.getMessage();
                controller.log(message);
                onFinished.accept(message);
                JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.ERROR_MESSAGE);
            });
    }

    private static void confirmAndApply(Rikr controller, Component parent, String sourceJar, String targetJar,
        MemberMatches matches, Consumer<String> onFinished) {
        List<ClassMappingDecision> classMappings = controller.mappings().accepted(sourceJar, targetJar);
        RenameOperations.NameTransfer given = controller.renames().planNameTransfer(sourceJar, targetJar,
            classMappings, matches, false);
        RenameOperations.NameTransfer loaded = controller.renames().planNameTransfer(sourceJar, targetJar,
            classMappings, matches, true);
        if (given.batch().size() == 0 && loaded.batch().size() == 0) {
            String message = describe(targetJar, matches, given) + String.format("%n%nNothing to rename: "
                + "no matched class or member of %s has a name to carry that %s doesn't have.", sourceJar, targetJar);
            JOptionPane.showMessageDialog(parent, message, TITLE, JOptionPane.INFORMATION_MESSAGE);
            onFinished.accept("Nothing to carry from " + sourceJar + ".");
            return;
        }

        JTextArea summary = new JTextArea();
        summary.setEditable(false);
        summary.setOpaque(false);
        summary.setFocusable(false);
        summary.setFont(UIManager.getFont("Label.font"));
        JCheckBox includeLoaded = new JCheckBox("Also carry the names " + sourceJar + " was loaded with");
        includeLoaded.setToolTipText("For a JAR that was named before it was loaded, such as a deobfuscated build. "
            + "Names a deobfuscator made up, such as class12 or field34, are not carried.");
        // A JAR named before it was loaded has nothing renamed in Rikr, so its loaded names are what to carry.
        includeLoaded.setSelected(given.batch().size() == 0);
        includeLoaded.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        // The button names what it does and how much, so the choice doesn't rest on reading the message.
        JButton rename = new JButton();
        JButton cancel = new JButton("Cancel");
        Runnable refresh = () -> {
            RenameOperations.NameTransfer transfer = includeLoaded.isSelected() ? loaded : given;
            summary.setText(String.format("Give %s the names from %s?%n%n", targetJar, sourceJar)
                + describe(targetJar, matches, transfer)
                + "\n\nMembers are matched by what their code does and how they are used, so check the ones that "
                + "matter.\nUndo reverts the whole rename.");
            rename.setText(transfer.batch().size() == 0 ? "Nothing to rename"
                : String.format("Rename %,d", transfer.batch().size()));
            rename.setEnabled(transfer.batch().size() > 0);
        };
        refresh.run();
        JOptionPane pane = new JOptionPane(new Object[]{summary, includeLoaded}, JOptionPane.QUESTION_MESSAGE,
            JOptionPane.DEFAULT_OPTION, null, new Object[]{rename, cancel}, rename);
        rename.addActionListener(event -> pane.setValue(rename));
        cancel.addActionListener(event -> pane.setValue(cancel));
        JDialog dialog = pane.createDialog(parent, TITLE);
        includeLoaded.addActionListener(event -> {
            refresh.run();
            dialog.pack();
        });
        dialog.getRootPane().setDefaultButton(rename);
        dialog.setVisible(true);
        dialog.dispose();
        if (pane.getValue() != rename) {
            onFinished.accept("Carrying names was cancelled.");
            return;
        }
        RenameBatch batch = (includeLoaded.isSelected() ? loaded : given).batch();
        try {
            RenameResult result = controller.renames().applyBatch(batch);
            String done = String.format("Gave %s the names from %s: %,d classes, %,d fields and %,d methods, "
                    + "changing %,d references in %,d classes.", targetJar, sourceJar, batch.classes().size(),
                batch.fields().size(), batch.methods().size(), result.referenceCount(), result.fileCount());
            controller.log(done);
            controller.getUserInterface().refreshCode();
            onFinished.accept(done);
        } catch (IllegalArgumentException exception) {
            // The JAR changed while the dialog was open, so the plan no longer fits.
            String failed = TITLE + " failed: " + exception.getMessage();
            controller.log(failed);
            onFinished.accept(failed);
            JOptionPane.showMessageDialog(parent, failed, TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String describe(String targetJar, MemberMatches matches,
        RenameOperations.NameTransfer transfer) {
        RenameBatch batch = transfer.batch();
        String kept = transfer.alreadyNamed() == 0 ? "" : String.format(
            "%n%,d matches in %s were renamed already and keep their names.", transfer.alreadyNamed(), targetJar);
        return String.format("Matched %,d of %,d methods and %,d of %,d fields.%n%nClasses renamed: %,d%n"
                + "Fields renamed: %,d%nMethods renamed: %,d (including overrides)",
            matches.methods().size(), matches.sourceMethods(), matches.fields().size(), matches.sourceFields(),
            batch.classes().size(), batch.fields().size(), batch.methods().size())
            + kept + describeLeftOut(transfer.leftOut());
    }

    private static String describeLeftOut(List<String> problems) {
        if (problems.isEmpty()) {
            return "";
        }
        List<String> shown = problems.stream().limit(PROBLEMS_SHOWN).toList();
        String more = problems.size() > PROBLEMS_SHOWN ? "\n...and " + (problems.size() - PROBLEMS_SHOWN) + " more." : "";
        return String.format(problems.size() == 1 ? "%n%n%,d can't be renamed and is left out:%n"
            : "%n%n%,d can't be renamed and are left out:%n", problems.size())
            + String.join("\n", shown) + more;
    }
}
