package org.de.rikr.ui.handler;

import org.de.rikr.Rikr;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.symbol.Symbol;
import org.de.rikr.symbol.SymbolResolver;
import org.de.rikr.ui.ContentPanel;
import org.de.rikr.ui.RenameDialog;
import org.de.rikr.ui.model.SymbolTarget;
import org.de.rikr.ui.model.TreeNodes;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;
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

    @Override
    public void actionPerformed(ActionEvent e) {
        Component invoker = ((JPopupMenu) ((JMenuItem) e.getSource()).getParent()).getInvoker();
        boolean fromCode = invoker instanceof JTextComponent;
        int caret = contentPanel.getPopupOffset();
        Optional<SymbolTarget> target = fromCode ? targetInCode(caret, true) : targetInTree();
        if (target.isEmpty()) {
            return;
        }
        SwingUtilities.invokeLater(() -> rename(target.get(), fromCode, caret));
    }

    /**
     * The class, field or method selected in the project tree.
     */
    public Optional<SymbolTarget> targetInTree() {
        return TreeNodes.targetOf(controller.getUserInterface().getTreePanel().getSelectedNode());
    }

    /**
     * The class, field or method named at {@code offset} of this panel's code, or selected there.
     *
     * @param warn whether to say why there is none
     */
    public Optional<SymbolTarget> targetInCode(int offset, boolean warn) {
        ClassNode displayed = contentPanel.getDisplayedClass();
        if (displayed == null) {
            return Optional.empty();
        }
        Optional<String> jar = controller.jars().jarOf(displayed);
        if (jar.isEmpty()) {
            return Optional.empty();
        }
        Optional<Declaration> declaration = contentPanel.isJavaOnly()
            ? declarationByName(displayed, offset, warn)
            : declarationAt(jar.get(), offset, warn);
        return declaration.map(value -> new SymbolTarget(jar.get(), value));
    }

    private Optional<Declaration> declarationAt(String jar, int offset, boolean warn) {
        Optional<Symbol> symbol = contentPanel.symbolAt(offset);
        if (symbol.isEmpty()) {
            if (warn) {
                warn("Put the caret on the name of a class, field or method, or right-click it.");
            }
            return Optional.empty();
        }
        Optional<Declaration> declaration = controller.jars().resolve(jar, symbol.get());
        if (declaration.isEmpty() && warn) {
            warn(describe(symbol.get()) + " is not declared in " + jar + ", so it cannot be renamed.");
        }
        return declaration;
    }

    private Optional<Declaration> declarationByName(ClassNode displayed, int offset, boolean warn) {
        String name = wordAt(offset);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        List<Declaration> matches = SymbolResolver.declaredByName(displayed, name);
        if (matches.size() == 1) {
            return Optional.of(matches.getFirst());
        }
        if (warn) {
            warn(matches.isEmpty()
                ? displayed.name + " declares nothing named " + name + ". Rename it from the bytecode view or the tree."
                : displayed.name + " declares more than one " + name + ". Rename it from the bytecode view or the tree.");
        }
        return Optional.empty();
    }

    private String wordAt(int offset) {
        JTextPane pane = contentPanel.getContentPane();
        String selected = pane.getSelectedText();
        if (selected != null && !selected.isBlank()) {
            return selected.trim();
        }
        try {
            int start = Utilities.getWordStart(pane, offset);
            int end = Utilities.getWordEnd(pane, offset);
            return pane.getDocument().getText(start, end - start).trim();
        } catch (BadLocationException ignored) {
            return "";
        }
    }

    /**
     * Ask for a new name, checked and with what it changes shown while it is typed, and rename.
     *
     * @param fromCode whether the target was chosen in the code, which then keeps its place
     * @param caret    where in the code to put the caret back
     */
    public void rename(SymbolTarget target, boolean fromCode, int caret) {
        JFrame frame = controller.getUserInterface().getFrame();
        Optional<String> newName = RenameDialog.ask(frame, target.name(),
            name -> controller.renames().preview(target.jar(), target.declaration(), name));
        if (newName.isEmpty()) {
            return;
        }

        // Renames keep node identity, so the same class node can be shown again afterwards; its old name has to be
        // read now, since the node carries the new one after.
        String oldName = target.name();
        ClassNode displayed = fromCode ? contentPanel.getDisplayedClass() : target.declaration().owner();
        RenameResult result = controller.renames().rename(target.jar(), target.declaration(), newName.get());
        if (target.declaration() instanceof Declaration.ClassDeclaration declaration
            && Modifier.isInterface(declaration.owner().access)) {
            result = result.withType("interface");
        }
        controller.log(String.format(
            "Renamed %s to %s: %d %s references in %d %s.",
            oldName,
            newName.get(),
            result.referenceCount(),
            result.type(),
            result.fileCount(),
            result.fileCount() == 1 ? "class" : "classes"
        ));
        controller.getUserInterface().displayBytecode(displayed);
        if (fromCode) {
            contentPanel.restoreCaret(caret);
        }
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
