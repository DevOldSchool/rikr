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
import javax.swing.text.Utilities;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
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
        String selectedText = getSelectedText(e);
        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            String newName = JOptionPane.showInputDialog(controller.getUserInterface().getFrame(), "Enter new name:", "Rename", JOptionPane.PLAIN_MESSAGE);
            if (newName == null || newName.trim().isEmpty()) {
                return;
            }

            DefaultMutableTreeNode selectedNode = getNodeToRename(e, selectedText);
            rename(selectedNode, newName);
        });
    }

    private String getSelectedText(ActionEvent e) {
        Component invoker = ((JPopupMenu) ((JMenuItem) e.getSource()).getParent()).getInvoker();
        if (invoker instanceof JTextPane) {
            return contentPane.getSelectedText();
        } else if (invoker instanceof JTree) {
            return controller.getUserInterface().getTreePanel().getSelectedNode().toString();
        }

        return "";
    }

    private DefaultMutableTreeNode getNodeToRename(ActionEvent e, String selectedText) {
        Component invoker = ((JPopupMenu) ((JMenuItem) e.getSource()).getParent()).getInvoker();
        if (invoker instanceof JTextPane) {
            return getNodeFromTextPane(selectedText);
        } else {
            return controller.getUserInterface().getTreePanel().getSelectedNode();
        }
    }

    private DefaultMutableTreeNode getNodeFromTextPane(String selectedText) {
        String lineText = getLineText(contentPane);
        if (lineText == null) {
            return controller.getUserInterface().getTreePanel().getSelectedNode();
        }

        DefaultMutableTreeNode selectedNode = controller.getUserInterface().getTreePanel().getSelectedNode();
        ClassNode classNode = getClassNode(selectedNode);

        if (classNode == null) {
            return null;
        }

        String type = "";
        String nodeName = "";

        if (lineText.contains("class " + classNode.name)) {
            type = "class";
            nodeName = classNode.name;
        } else if (lineText.contains("interface " + classNode.name)) {
            type = "interface";
            nodeName = classNode.name;
        } else {
            for (FieldNode fieldNode : classNode.fields) {
                String fieldPattern = fieldNode.desc + " " + selectedText;
                if (lineText.contains(fieldPattern)) {
                    type = "field";
                    nodeName = fieldNode.name + " " + fieldNode.desc;
                    break;
                }
            }

            for (MethodNode methodNode : classNode.methods) {
                String methodPattern = selectedText + methodNode.desc;
                if (lineText.contains(methodPattern)) {
                    type = "method";
                    nodeName = methodNode.name + " " + methodNode.desc;
                    break;
                }
            }
        }

        return getSelectedNode(selectedNode, type, nodeName);
    }

    private String getLineText(JTextPane textPane) {
        try {
            int caretPosition = textPane.getCaretPosition();
            int lineStart = Utilities.getRowStart(textPane, caretPosition);
            int lineEnd = Utilities.getRowEnd(textPane, caretPosition);

            return textPane.getDocument().getText(lineStart, lineEnd - lineStart);
        } catch (BadLocationException ignored) {
            return null;
        }
    }

    private ClassNode getClassNode(DefaultMutableTreeNode selectedNode) {
        if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
            return classMutableTreeNode.getClassNode();
        } else if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            return classNodeMutableTreeNode.getClassNode();
        } else if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNodeMutableTreeNode) {
            return interfaceNodeMutableTreeNode.getClassNode();
        } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNodeMutableTreeNode) {
            return fieldNodeMutableTreeNode.getOwner();
        } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
            return methodNodeMutableTreeNode.getOwner();
        }

        return null;
    }

    private DefaultMutableTreeNode getSelectedNode(DefaultMutableTreeNode selectedNode, String type, String nodeName) {
        return switch (type) {
            case "class", "interface" -> getClassOrInterfaceNode(selectedNode, nodeName);
            case "field", "method" -> getFieldOrMethodNode(selectedNode, nodeName);
            default -> selectedNode;
        };
    }

    private DefaultMutableTreeNode getClassOrInterfaceNode(DefaultMutableTreeNode selectedNode, String nodeName) {
        if (selectedNode instanceof ClassMutableTreeNode) {
            return controller.getUserInterface().getTreePanel().findChild(selectedNode, nodeName);
        } else if (selectedNode instanceof FieldNodeMutableTreeNode ||
                selectedNode instanceof MethodNodeMutableTreeNode) {
            return (DefaultMutableTreeNode) selectedNode.getParent();
        }

        return selectedNode;
    }

    private DefaultMutableTreeNode getFieldOrMethodNode(DefaultMutableTreeNode selectedNode, String nodeName) {
        if (selectedNode instanceof ClassMutableTreeNode ||
                selectedNode instanceof ClassNodeMutableTreeNode ||
                selectedNode instanceof InterfaceNodeMutableTreeNode) {
            return controller.getUserInterface().getTreePanel().findChildRecursively(selectedNode, nodeName);
        } else if (selectedNode instanceof FieldNodeMutableTreeNode ||
                selectedNode instanceof MethodNodeMutableTreeNode) {
            return controller.getUserInterface().getTreePanel().findChild((DefaultMutableTreeNode) selectedNode.getParent(), nodeName);
        }

        return selectedNode;
    }

    private void rename(DefaultMutableTreeNode selectedNode, String newName) {
        ClassNode classNode = null;

        if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
            classNode = handleClassMutableTreeNode(classMutableTreeNode, newName);
        } else if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            classNode = handleClassNodeMutableTreeNode(classNodeMutableTreeNode, newName);
        } else if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNodeMutableTreeNode) {
            classNode = handleInterfaceNodeMutableTreeNode(interfaceNodeMutableTreeNode, newName);
        } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNodeMutableTreeNode) {
            classNode = handleFieldNodeMutableTreeNode(fieldNodeMutableTreeNode, newName);
        } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
            classNode = handleMethodNodeMutableTreeNode(methodNodeMutableTreeNode, newName);
        }

        if (classNode != null) {
            updateNodeAndDisplay(classNode, selectedNode, newName);
        }
    }

    private ClassNode handleClassMutableTreeNode(ClassMutableTreeNode node, String newName) {
        String jarName = node.getJarName();
        ClassNode classNode = node.getClassNode();

        if (classNode != null) {
            Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
            logRenameResults("class", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter());
            node.setUserObject(newName + ".class");
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(0);

            if (childNode != null) {
                childNode.setUserObject(newName);
                controller.getUserInterface().getTreePanel().updateNode(childNode);
            }
        }

        return classNode;
    }

    private ClassNode handleClassNodeMutableTreeNode(ClassNodeMutableTreeNode node, String newName) {
        ClassMutableTreeNode parentNode = (ClassMutableTreeNode) node.getParent();
        String jarName = node.getJarName();
        ClassNode classNode = node.getClassNode();

        if (classNode != null) {
            Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
            logRenameResults("class", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter());
            node.setUserObject(newName);
            parentNode.setUserObject(newName + ".class");
            controller.getUserInterface().getTreePanel().updateNode(parentNode);
        }

        return classNode;
    }

    private ClassNode handleInterfaceNodeMutableTreeNode(InterfaceNodeMutableTreeNode node, String newName) {
        ClassMutableTreeNode parentNode = (ClassMutableTreeNode) node.getParent();
        String jarName = node.getJarName();
        ClassNode classNode = node.getClassNode();

        if (classNode != null) {
            Renamer.updateClassReferences(controller.getClasses(jarName), classNode.name, newName);
            logRenameResults("interface", Renamer.getRenamedClassReferenceCounter(), Renamer.getRenamedClassReferenceFileCounter());
            node.setUserObject(newName);
            parentNode.setUserObject(newName + ".class");
            controller.getUserInterface().getTreePanel().updateNode(parentNode);
        }

        return classNode;
    }

    private ClassNode handleFieldNodeMutableTreeNode(FieldNodeMutableTreeNode node, String newName) {
        ClassMutableTreeNode parentNode = (ClassMutableTreeNode) node.getParent().getParent();
        String jarName = parentNode.getJarName();
        ClassNode classNode = node.getOwner();
        FieldNode fieldNode = node.getFieldNode();

        if (classNode != null && fieldNode != null) {
            Renamer.updateFieldReferences(controller.getClasses(jarName), classNode, fieldNode.name, newName);
            logRenameResults("field", Renamer.getRenamedFieldReferenceCounter(), Renamer.getRenamedFieldReferenceFileCounter());
            node.setUserObject(newName + " " + fieldNode.desc);
        }

        return classNode;
    }

    private ClassNode handleMethodNodeMutableTreeNode(MethodNodeMutableTreeNode node, String newName) {
        ClassMutableTreeNode parentNode = (ClassMutableTreeNode) node.getParent().getParent();
        String jarName = parentNode.getJarName();
        ClassNode classNode = node.getOwner();
        MethodNode methodNode = node.getMethodNode();

        if (classNode != null && methodNode != null) {
            Renamer.updateMethodReferences(controller.getClasses(jarName), classNode, methodNode.name, newName);
            logRenameResults("method", Renamer.getRenamedMethodReferenceCounter(), Renamer.getRenamedMethodReferenceFileCounter());
            node.setUserObject(newName + " " + methodNode.desc);
        }

        return classNode;
    }

    private void logRenameResults(String type, int renamedCount, int fileCount) {
        controller.log(String.format("Renamed %d %s references in %d files.", renamedCount, type, fileCount));
    }

    private void updateNodeAndDisplay(ClassNode classNode, DefaultMutableTreeNode selectedNode, String newName) {
        controller.getUserInterface().getTreePanel().updateNode(selectedNode);
        controller.getUserInterface().displayBytecode(classNode);
        restoreCaretAndHighlight(newName);
    }

    private void restoreCaretAndHighlight(String newName) {
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
    }
}