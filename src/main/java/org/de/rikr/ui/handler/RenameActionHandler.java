package org.de.rikr.ui.handler;

import org.de.rikr.Renamer;
import org.de.rikr.Rikr;
import org.de.rikr.ui.model.ClassMutableTreeNode;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.de.rikr.ui.model.FieldNodeMutableTreeNode;
import org.de.rikr.ui.model.MethodNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RenameActionHandler implements ActionListener {
    private final Rikr controller;
    private final JTextPane contentPane;
    private final StyledDocument document;
    private final int[] originalCaretPosition;

    public RenameActionHandler(Rikr controller, JTextPane contentPane, StyledDocument document, int[] originalCaretPosition) {
        this.controller = controller;
        this.contentPane = contentPane;
        this.document = document;
        this.originalCaretPosition = originalCaretPosition;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String selectedText;
        if (e.getSource() instanceof JTextPane) {
            selectedText = contentPane.getSelectedText();
        } else if (e.getSource() instanceof JMenuItem) {
            selectedText = controller.getTreePanel().getSelectedNode().toString();
        } else {
            selectedText = "";
        }

        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

        // Show input dialog to get the new name from the user
        SwingUtilities.invokeLater(() -> {
            String newName = JOptionPane.showInputDialog(controller.getUserInterface().getFrame(), "Enter new name:", "Rename", JOptionPane.PLAIN_MESSAGE);
            if (newName == null || newName.trim().isEmpty()) {
                return;
            }

            DefaultMutableTreeNode selectedNode = controller.getTreePanel().getSelectedNode();

            if (selectedNode instanceof ClassMutableTreeNode | selectedNode instanceof ClassNodeMutableTreeNode) {
                String jarName;
                ClassNode classNode;

                if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                    jarName = classMutableTreeNode.getJarName();
                    classNode = classMutableTreeNode.getClassNode();
                } else {
                    ClassNodeMutableTreeNode classNodeMutableTreeNode = (ClassNodeMutableTreeNode) selectedNode;
                    ClassMutableTreeNode parentNode = (ClassMutableTreeNode) classNodeMutableTreeNode.getParent();
                    jarName = parentNode.getJarName();
                    classNode = classNodeMutableTreeNode.getClassNode();
                }

                if (classNode == null || !classNode.name.equals(selectedText.replace(".class", ""))) {
                    return;
                }

                Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
                controller.log(String.format("Renamed %d class references in %d files.", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter()));

                selectedNode.setUserObject(newName + ".class");
                System.out.println("Selected node: " + selectedNode);
                controller.getTreePanel().updateNode(selectedNode);
                controller.displayBytecode(classNode);

                // Restore caret position and highlight the renamed text
                SwingUtilities.invokeLater(() -> {
                    contentPane.setCaretPosition(originalCaretPosition[0]);
                    try {
                        int start = document.getText(0, document.getLength()).indexOf(newName);
                        if (start >= 0) {
                            contentPane.select(start, start + newName.length());
                        }
                    } catch (BadLocationException ignored) {
                    }
                });
            } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNodeMutableTreeNode) {
                // TODO support renaming fields
                System.out.println("Selected field " + fieldNodeMutableTreeNode.getFieldNode().name);
            } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
                // TODO support renaming methods
                System.out.println("Selected method " + methodNodeMutableTreeNode.getMethodNode().name);
            }
        });
    }
}