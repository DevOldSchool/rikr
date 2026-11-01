package org.de.rikr.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Optional;

/**
 * Undo and Redo for typing in any editable text field. Swing text fields keep no undo history of their own, so
 * Cmd+Z in a search field did nothing, or, worse, would have undone a rename. A field gets its history when it
 * first takes the focus. In the main window the Edit menu's Undo and Redo act on it; a dialog has no menu bar, so
 * there the field gets Cmd+Z and Shift+Cmd+Z bindings of its own.
 */
final class TextUndo {
    private static final String HISTORY = "rikr.textUndo";
    private static final int MENU = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final Runnable changed;
    private JTextComponent focused;

    /**
     * @param changed told when the focused field or what it can undo changes, so the menu can say so
     */
    TextUndo(Runnable changed) {
        this.changed = changed;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner",
            event -> focusMoved(event.getNewValue()));
    }

    /**
     * Whether an editable text field has the focus, so Undo and Redo belong to its typing rather than to renames.
     */
    boolean active() {
        return history().isPresent();
    }

    boolean canUndo() {
        return history().map(UndoManager::canUndo).orElse(false);
    }

    boolean canRedo() {
        return history().map(UndoManager::canRedo).orElse(false);
    }

    void undo() {
        history().filter(UndoManager::canUndo).ifPresent(UndoManager::undo);
        changed.run();
    }

    void redo() {
        history().filter(UndoManager::canRedo).ifPresent(UndoManager::redo);
        changed.run();
    }

    private Optional<UndoManager> history() {
        if (focused == null || !focused.isEditable() || !focused.isEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable((UndoManager) focused.getClientProperty(HISTORY));
    }

    private void focusMoved(Object owner) {
        // Focus can pass to a menu or a popup and come back; only a new focus owner in a window changes the field.
        if (owner == null) {
            return;
        }
        focused = owner instanceof JTextComponent text && text.isEditable() ? text : null;
        if (focused != null && focused.getClientProperty(HISTORY) == null) {
            install(focused);
        }
        changed.run();
    }

    private void install(JTextComponent text) {
        UndoManager history = new TypingHistory();
        text.putClientProperty(HISTORY, history);
        text.getDocument().addUndoableEditListener(history);
        text.getDocument().addUndoableEditListener(event -> changed.run());
        // A new document starts a new history; edits to the old one can't be undone in the new.
        PropertyChangeListener documentReplaced = event -> {
            if (event.getOldValue() instanceof Document old) {
                old.removeUndoableEditListener(history);
            }
            if (event.getNewValue() instanceof Document replacement) {
                replacement.addUndoableEditListener(history);
            }
            history.discardAllEdits();
            changed.run();
        };
        text.addPropertyChangeListener("document", documentReplaced);
        if (!(SwingUtilities.getWindowAncestor(text) instanceof JFrame frame && frame.getJMenuBar() != null)) {
            bind(text, KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU), "rikr.undoTyping", history, true);
            bind(text, KeyStroke.getKeyStroke(KeyEvent.VK_Z, MENU | KeyEvent.SHIFT_DOWN_MASK), "rikr.redoTyping",
                history, false);
        }
    }

    private static void bind(JTextComponent text, KeyStroke key, String name, UndoManager history, boolean undo) {
        text.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        text.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (undo && history.canUndo()) {
                    history.undo();
                } else if (!undo && history.canRedo()) {
                    history.redo();
                }
            }
        });
    }

    /**
     * An undo history in which a run of typing, or of deleting, is one step, as in other macOS text fields; undoing
     * a word a letter at a time is no use. A run ends after a pause of a second or when typing turns to deleting.
     */
    private static final class TypingHistory extends UndoManager {
        private static final long PAUSE_MILLIS = 1000;

        private CompoundEdit run;
        private DocumentEvent.EventType runType;
        private long lastEdit;

        @Override
        public synchronized void undoableEditHappened(UndoableEditEvent event) {
            UndoableEdit edit = event.getEdit();
            DocumentEvent.EventType type = edit instanceof DocumentEvent change ? change.getType() : null;
            long now = System.currentTimeMillis();
            if (run == null || !run.isInProgress() || type != runType || now - lastEdit > PAUSE_MILLIS) {
                endRun();
                run = new CompoundEdit();
                runType = type;
                addEdit(run);
            }
            run.addEdit(edit);
            lastEdit = now;
        }

        // The run being typed is the newest step, so it can be undone even before it ends.
        @Override
        public synchronized boolean canUndo() {
            return run != null && run.isInProgress() || super.canUndo();
        }

        @Override
        public synchronized void undo() {
            endRun();
            super.undo();
        }

        @Override
        public synchronized void redo() {
            endRun();
            super.redo();
        }

        @Override
        public synchronized void discardAllEdits() {
            endRun();
            super.discardAllEdits();
        }

        private void endRun() {
            if (run != null && run.isInProgress()) {
                run.end();
            }
            run = null;
        }
    }
}
