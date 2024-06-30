package org.de.rikr.ui.handler;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;

public class TreeSelectionHandler implements TreeSelectionListener {
    private final Rikr controller;
    private final JTree tree;
    private DefaultMutableTreeNode selectedNode;

    public TreeSelectionHandler(Rikr controller, JTree tree) {
        this.controller = controller;
        this.tree = tree;
    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {
        selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

        // Selects and highlights the nodes corresponding data in the document
        if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
            controller.displayBytecode(classMutableTreeNode.getClassNode());
        } else if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            controller.displayBytecode(classNodeMutableTreeNode.getClassNode());

            SwingUtilities.invokeLater(() -> {
                ClassNode classNode = classNodeMutableTreeNode.getClassNode();
                controller.getContentPanel().selectTextAfterPattern("class " + classNode.name, classNode.name);
            });
        } else if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNodeMutableTreeNode) {
            controller.displayBytecode(interfaceNodeMutableTreeNode.getClassNode());

            SwingUtilities.invokeLater(() -> {
                ClassNode classNode = interfaceNodeMutableTreeNode.getClassNode();
                controller.getContentPanel().selectTextAfterPattern("interface " + classNode.name, classNode.name);
            });
        } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNodeMutableTreeNode) {
            ClassMutableTreeNode classMutableTreeNode = (ClassMutableTreeNode) selectedNode.getParent().getParent();
            controller.displayBytecode(classMutableTreeNode.getClassNode());

            SwingUtilities.invokeLater(() -> {
                FieldNode fieldNode = fieldNodeMutableTreeNode.getFieldNode();
                controller.getContentPanel().selectTextAfterPattern(fieldNode.desc + " " + fieldNode.name, fieldNode.name);
            });
        } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
            ClassMutableTreeNode classMutableTreeNode = (ClassMutableTreeNode) selectedNode.getParent().getParent();
            controller.displayBytecode(classMutableTreeNode.getClassNode());

            SwingUtilities.invokeLater(() -> {
                MethodNode methodNode = methodNodeMutableTreeNode.getMethodNode();
                controller.getContentPanel().selectTextAfterPattern(methodNode.name + methodNode.desc, methodNode.name);
            });
        }
    }

    public DefaultMutableTreeNode getSelectedNode() {
        return selectedNode;
    }
}
