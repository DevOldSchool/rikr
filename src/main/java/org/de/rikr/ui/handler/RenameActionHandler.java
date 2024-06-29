package org.de.rikr.ui.handler;

import org.de.rikr.Renamer;
import org.de.rikr.Rikr;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

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
            String jarName;
            ClassNode classNode = null;

            if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) classMutableTreeNode.getChildAt(0);
                jarName = classMutableTreeNode.getJarName();
                classNode = classMutableTreeNode.getClassNode();

                if (classNode == null) {
                    return;
                }

                Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
                controller.log(String.format("Renamed %d class references in %d files.", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter()));
                selectedNode.setUserObject(newName + ".class");

                if (childNode != null) {
                    childNode.setUserObject(newName);
                    controller.getTreePanel().updateNode(childNode);
                }
            } else if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
                ClassMutableTreeNode parentNode = (ClassMutableTreeNode) classNodeMutableTreeNode.getParent();
                jarName = parentNode.getJarName();
                classNode = classNodeMutableTreeNode.getClassNode();

                if (classNode == null) {
                    return;
                }

                Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
                controller.log(String.format("Renamed %d class references in %d files.", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter()));
                selectedNode.setUserObject(newName);
                parentNode.setUserObject(newName + ".class");
                controller.getTreePanel().updateNode(parentNode);
            } else if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNodeMutableTreeNode) {
                ClassMutableTreeNode parentNode = (ClassMutableTreeNode) interfaceNodeMutableTreeNode.getParent();
                jarName = parentNode.getJarName();
                classNode = interfaceNodeMutableTreeNode.getClassNode();

                if (classNode == null) {
                    return;
                }

                Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
                controller.log(String.format("Renamed %d interface references in %d files.", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter()));
                selectedNode.setUserObject(newName);
                parentNode.setUserObject(newName + ".class");
                controller.getTreePanel().updateNode(parentNode);
            } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNodeMutableTreeNode) {
                ClassMutableTreeNode parentNode = (ClassMutableTreeNode) fieldNodeMutableTreeNode.getParent().getParent();
                jarName = parentNode.getJarName();
                classNode = parentNode.getClassNode();
                FieldNode fieldNode = fieldNodeMutableTreeNode.getFieldNode();

                if (classNode == null || fieldNode == null) {
                    return;
                }

                Renamer.updateFieldReferences(controller.getClasses(jarName), classNode, fieldNode.name, newName);
                controller.log(String.format("Renamed %d field references in %d files.", Renamer.getRenamedFieldReferenceCounter(), Renamer.getRenamedFieldReferenceFileCounter()));
                selectedNode.setUserObject(newName + " " + fieldNode.desc);
            } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
                ClassMutableTreeNode parentNode = (ClassMutableTreeNode) methodNodeMutableTreeNode.getParent().getParent();
                jarName = parentNode.getJarName();
                classNode = parentNode.getClassNode();
                MethodNode methodNode = methodNodeMutableTreeNode.getMethodNode();

                if (classNode == null || methodNode == null) {
                    return;
                }

                Renamer.updateMethodReferences(controller.getClasses(jarName), classNode, methodNode.name, newName);
                controller.log(String.format("Renamed %d method references in %d files.", Renamer.getRenamedMethodReferenceCounter(), Renamer.getRenamedMethodReferenceFileCounter()));
                selectedNode.setUserObject(newName + " " + methodNode.desc);
            }

            if (classNode == null) {
                return;
            }

            // Update and select node
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
        });
    }
}