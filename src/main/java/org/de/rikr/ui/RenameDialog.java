package org.de.rikr.ui;

import com.formdev.flatlaf.FlatLaf;
import org.de.rikr.rename.RenamePreview;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.Function;

/**
 * Asks for a new name and checks it while it is typed: a name that cannot be used is said under the field, and a
 * usable one says how many references change. Enter renames; there is no confirmation, since Undo reverses a rename.
 */
public final class RenameDialog {
    // Long enough that checking does not run on every keystroke of a quick typist; checking a widely used name
    // scans every class of the JAR.
    private static final int CHECK_DELAY_MS = 150;

    private RenameDialog() {
    }

    /**
     * @param check the preview of renaming to a name, which says whether it can be used and what it changes
     * @return the new name, or empty if the person cancelled
     */
    public static Optional<String> ask(Window owner, String oldName, Function<String, RenamePreview> check) {
        JDialog dialog = new JDialog(owner, "Rename", Dialog.ModalityType.APPLICATION_MODAL);
        JLabel label = new JLabel("New name for " + oldName);
        JTextField field = new JTextField(oldName, 28);
        label.setLabelFor(field);
        field.getAccessibleContext().setAccessibleName("New name for " + oldName);
        JLabel message = new JLabel(" ");
        JButton cancel = new JButton("Cancel");
        JButton rename = new JButton("Rename");
        rename.setEnabled(false);

        String[] chosen = {null};
        Timer timer = new Timer(CHECK_DELAY_MS, event -> show(check, oldName, field, message, rename));
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                pending();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                pending();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                pending();
            }

            private void pending() {
                rename.setEnabled(false);
                timer.restart();
            }
        });
        Runnable accept = () -> {
            timer.stop();
            // Check again now, so Enter typed faster than the delay never renames to an unchecked name.
            show(check, oldName, field, message, rename);
            if (rename.isEnabled()) {
                chosen[0] = field.getText().trim();
                dialog.dispose();
            }
        };
        rename.addActionListener(event -> accept.run());
        field.addActionListener(event -> accept.run());
        cancel.addActionListener(event -> dialog.dispose());
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        for (JComponent component : new JComponent[]{label, field, message}) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        fields.add(label);
        fields.add(Box.createVerticalStrut(6));
        fields.add(field);
        fields.add(Box.createVerticalStrut(6));
        fields.add(message);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(rename);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(fields, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(rename);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        field.selectAll();
        // The unchanged name is not a rename; say so rather than leave the line empty.
        show(check, oldName, field, message, rename);
        dialog.setVisible(true);
        return Optional.ofNullable(chosen[0]);
    }

    private static void show(Function<String, RenamePreview> check, String oldName, JTextField field, JLabel message,
                             JButton rename) {
        String name = field.getText().trim();
        String problem = null;
        String impact = null;
        if (name.isEmpty()) {
            problem = "Type a name.";
        } else if (name.equals(oldName)) {
            impact = "Type the new name.";
        } else {
            RenamePreview preview = check.apply(name);
            if (preview.valid()) {
                impact = plural(preview.referenceCount(), "reference") + " in "
                    + plural(preview.fileCount(), "class") + " change.";
                message.setToolTipText(preview.affectedClasses().isEmpty() ? null
                    : "<html>" + String.join("<br>", preview.affectedClasses().stream().limit(30).toList())
                    + (preview.affectedClasses().size() > 30 ? "<br>…" : "") + "</html>");
            } else {
                problem = preview.validationMessage();
            }
        }
        boolean usable = problem == null && !name.equals(oldName);
        rename.setEnabled(usable);
        field.putClientProperty("JComponent.outline", problem == null ? null : "error");
        message.setText(problem != null ? problem : impact);
        message.setForeground(problem != null ? errorColor() : UIManager.getColor("Label.foreground"));
        if (problem != null) {
            message.setToolTipText(null);
        }
        // Said to a screen reader along with the field, so the reason does not depend on seeing the colour.
        field.getAccessibleContext().setAccessibleDescription(message.getText());
    }

    // Holds 4.5:1 against the dialog background of both themes.
    private static Color errorColor() {
        return FlatLaf.isLafDark() ? new Color(0xFF, 0x8A, 0x80) : new Color(0xB0, 0x24, 0x18);
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : noun.endsWith("s") ? "es" : "s");
    }
}
