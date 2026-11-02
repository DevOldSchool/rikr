package org.de.rikr.ui.action;

import org.de.rikr.Rikr;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.ui.ClassViewer;
import org.de.rikr.ui.ContentPanel;
import org.de.rikr.ui.TreePanel;
import org.de.rikr.ui.model.SymbolTarget;
import org.de.rikr.ui.model.TreeNodes;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * The actions on a class, field or method that the Code menu, its shortcuts and the context menus share. From the
 * menu bar they act on what has the keyboard focus: the selection in the project tree, or the name at the caret in
 * a code view.
 */
public final class SymbolActions {

    /**
     * An action on one class, field or method.
     */
    public enum Action {
        RENAME,
        SHOW_REFERENCES,
        TOGGLE_BOOKMARK,
        SHOW_CONTROL_FLOW,
        SHOW_CALL_GRAPH,
        SIMULATE_METHOD
    }

    private final Rikr controller;

    public SymbolActions(Rikr controller) {
        this.controller = controller;
    }

    /**
     * Run the action on the class, field or method that has the focus. Says what to choose when nothing is chosen.
     */
    public void run(Action action) {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        ContentPanel code = focused == null ? null
            : (ContentPanel) SwingUtilities.getAncestorOfClass(ContentPanel.class, focused);
        if (code != null) {
            run(action, code, code.getContentPane().getCaretPosition());
            return;
        }
        TreePanel tree = controller.getUserInterface().getTreePanel();
        Optional<SymbolTarget> target = TreeNodes.targetOf(tree.getSelectedNode());
        if (target.isEmpty()) {
            controller.log("Select a class, field or method in the project tree or in the code first.");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        run(action, target.get(), null, -1);
    }

    /**
     * Run the action on the class, field or method named at {@code offset} in a code view.
     */
    public void run(Action action, ContentPanel code, int offset) {
        code.getRenameActionHandler().targetInCode(offset, true)
            .ifPresent(target -> run(action, target, code, offset));
    }

    /**
     * Run the action on the class, field or method chosen in the tree.
     */
    public void run(Action action, SymbolTarget target) {
        run(action, target, null, -1);
    }

    private void run(Action action, SymbolTarget target, ContentPanel code, int offset) {
        ClassViewer userInterface = controller.getUserInterface();
        switch (action) {
            case RENAME -> {
                ContentPanel handlerPanel = code != null ? code : userInterface.getContentPanel();
                SwingUtilities.invokeLater(() ->
                    handlerPanel.getRenameActionHandler().rename(target, code != null, offset));
            }
            case SHOW_REFERENCES -> {
                switch (target.declaration()) {
                    case Declaration.ClassDeclaration value ->
                        userInterface.showClassReferences(target.jar(), value.owner());
                    case Declaration.FieldDeclaration value ->
                        userInterface.showFieldReferences(target.jar(), value.owner(), value.field());
                    case Declaration.MethodDeclaration value ->
                        userInterface.showMethodReferences(target.jar(), value.owner(), value.method());
                }
            }
            case TOGGLE_BOOKMARK -> controller.toggleBookmark(TreeNodes.bookmarkOf(target));
            case SHOW_CONTROL_FLOW, SHOW_CALL_GRAPH, SIMULATE_METHOD -> {
                if (!(target.declaration() instanceof Declaration.MethodDeclaration method)) {
                    controller.log(target.name() + " is not a method. Choose a method to see its control flow, "
                        + "its calls or to simulate it.");
                    Toolkit.getDefaultToolkit().beep();
                    return;
                }
                switch (action) {
                    case SHOW_CONTROL_FLOW -> userInterface.showControlFlow(method.owner(), method.method());
                    case SHOW_CALL_GRAPH -> userInterface.showCallGraph(method.owner(), method.method());
                    default -> userInterface.getMethodSimulatorPanel().initSimulate(method.owner(), method.method());
                }
            }
        }
    }

    /**
     * Whether the class, field or method is bookmarked, so a menu can say Add or Remove.
     */
    public boolean isBookmarked(SymbolTarget target) {
        return controller.bookmarks().contains(TreeNodes.bookmarkOf(target));
    }
}
