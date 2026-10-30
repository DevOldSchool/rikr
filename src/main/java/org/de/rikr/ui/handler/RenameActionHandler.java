package org.de.rikr.ui.handler;

import org.de.rikr.Rikr;
import org.de.rikr.rename.RenamePreview;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.symbol.Symbol;
import org.de.rikr.symbol.SymbolResolver;
import org.de.rikr.ui.ContentPanel;
import org.de.rikr.ui.model.TreeNodes;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;

/**
 * Renames the class, field or method chosen in the tree or in a code view. In the bytecode view the name under
 * the click is looked up in the symbols recorded while printing the class. The Java view has no such record,
 * so a name there must match exactly one declaration of the class on screen.
 */
public class RenameActionHandler implements ActionListener {
    private final Rikr controller;
    private final ContentPanel contentPanel;

    public RenameActionHandler(Rikr controller, ContentPanel contentPanel) {
        this.controller = controller;
        this.contentPanel = contentPanel;
    }

    private record RenameTarget(String jar, Declaration declaration) {
        String currentName() {
            return switch (declaration) {
                case Declaration.ClassDeclaration target -> target.owner().name;
                case Declaration.FieldDeclaration target -> target.field().name;
                case Declaration.MethodDeclaration target -> target.method().name;
            };
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Component invoker = ((JPopupMenu) ((JMenuItem) e.getSource()).getParent()).getInvoker();
        boolean fromCode = invoker instanceof JTextComponent;
        Optional<RenameTarget> target = fromCode ? targetInCode() : targetInTree();
        if (target.isEmpty()) {
            return;
        }
        int caret = contentPanel.getPopupOffset();
        SwingUtilities.invokeLater(() -> rename(target.get(), fromCode, caret));
    }

    private Optional<RenameTarget> targetInTree() {
        DefaultMutableTreeNode node = controller.getUserInterface().getTreePanel().getSelectedNode();
        Optional<String> jar = TreeNodes.jarOf(node);
        return jar.flatMap(name -> TreeNodes.declarationOf(node).map(value -> new RenameTarget(name, value)));
    }

    private Optional<RenameTarget> targetInCode() {
        ClassNode displayed = contentPanel.getDisplayedClass();
        if (displayed == null) {
            return Optional.empty();
        }
        Optional<String> jar = controller.jars().jarOf(displayed);
        if (jar.isEmpty()) {
            return Optional.empty();
        }
        Optional<Declaration> declaration = contentPanel.isJavaOnly()
            ? declarationByName(displayed)
            : declarationAtClick(jar.get());
        return declaration.map(value -> new RenameTarget(jar.get(), value));
    }

    private Optional<Declaration> declarationAtClick(String jar) {
        Optional<Symbol> symbol = contentPanel.symbolAt(contentPanel.getPopupOffset());
        if (symbol.isEmpty()) {
            warn("Right-click the name of a class, field or method to rename it.");
            return Optional.empty();
        }
        Optional<Declaration> declaration = controller.jars().resolve(jar, symbol.get());
        if (declaration.isEmpty()) {
            warn(describe(symbol.get()) + " is not declared in " + jar + ", so it cannot be renamed.");
        }
        return declaration;
    }

    private Optional<Declaration> declarationByName(ClassNode displayed) {
        String name = wordAtClick();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        List<Declaration> matches = SymbolResolver.declaredByName(displayed, name);
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }
        warn(matches.isEmpty()
            ? displayed.name + " declares nothing named " + name + ". Rename it from the bytecode view or the tree."
            : displayed.name + " declares more than one " + name + ". Rename it from the bytecode view or the tree.");
        return Optional.empty();
    }

    private String wordAtClick() {
        JTextPane pane = contentPanel.getContentPane();
        String selected = pane.getSelectedText();
        if (selected != null && !selected.isBlank()) {
            return selected.trim();
        }
        try {
            int offset = contentPanel.getPopupOffset();
            int start = Utilities.getWordStart(pane, offset);
            int end = Utilities.getWordEnd(pane, offset);
            return pane.getDocument().getText(start, end - start).trim();
        } catch (BadLocationException ignored) {
            return "";
        }
    }

    private void rename(RenameTarget target, boolean fromCode, int caret) {
        JFrame frame = controller.getUserInterface().getFrame();
        Object input = JOptionPane.showInputDialog(
            frame, "New name for " + target.currentName() + ":", "Rename", JOptionPane.PLAIN_MESSAGE, null, null,
            target.currentName()
        );
        if (input == null || input.toString().trim().isEmpty()) {
            return;
        }
        String newName = input.toString().trim();

        RenamePreview preview = controller.renames().preview(target.jar(), target.declaration(), newName);
        if (!preview.valid()) {
            JOptionPane.showMessageDialog(frame, preview.validationMessage(), "Rename Not Available",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!confirm(frame, preview)) {
            return;
        }

        // Renames keep node identity, so the same class node can be shown again afterwards.
        ClassNode displayed = fromCode ? contentPanel.getDisplayedClass() : target.declaration().owner();
        RenameResult result = controller.renames().rename(target.jar(), target.declaration(), newName);
        if (target.declaration() instanceof Declaration.ClassDeclaration declaration
            && Modifier.isInterface(declaration.owner().access)) {
            result = result.withType("interface");
        }
        controller.log(String.format(
            "Renamed %d %s references in %d files.",
            result.referenceCount(),
            result.type(),
            result.fileCount()
        ));
        controller.getUserInterface().displayBytecode(displayed);
        if (fromCode) {
            contentPanel.restoreCaret(caret);
        }
    }

    /**
     * Show what the rename changes and ask to go ahead. The class list scrolls, so the dialog fits the screen
     * however many classes a widely used name touches.
     */
    private static boolean confirm(JFrame frame, RenamePreview preview) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        int classes = preview.affectedClasses().size();
        panel.add(new JLabel("<html><b>Rename " + escape(preview.oldName()) + " to " + escape(preview.newName())
            + "?</b><br><br>" + plural(preview.referenceCount(), "reference") + " in "
            + plural(preview.fileCount(), "class") + " change.</html>"), BorderLayout.NORTH);
        if (classes > 0) {
            JList<String> list = new JList<>(preview.affectedClasses().toArray(String[]::new));
            list.setVisibleRowCount(Math.min(classes, 10));
            list.getAccessibleContext().setAccessibleName("Classes that change");
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(360, scroll.getPreferredSize().height));
            panel.add(scroll, BorderLayout.CENTER);
        }
        Object[] options = {"Rename", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame, panel, "Rename", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return choice == 0;
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : noun.endsWith("s") ? "es" : "s");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(controller.getUserInterface().getFrame(), message, "Rename",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private static String describe(Symbol symbol) {
        return switch (symbol) {
            case Symbol.ClassSymbol value -> value.className();
            case Symbol.FieldSymbol value -> value.owner() + "." + value.name();
            case Symbol.MethodSymbol value -> value.owner() + "." + value.name() + value.descriptor();
        };
    }
}
