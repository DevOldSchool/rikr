package org.de.rikr.ui.action;

import org.de.rikr.Rikr;
import org.de.rikr.ui.TreePanel;
import org.de.rikr.ui.model.ClassMutableTreeNode;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.de.rikr.ui.model.JarMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;

public class ClassFileActions {
    private final Rikr controller;
    private final JFrame frame;
    private final TreePanel treePanel;

    public ClassFileActions(Rikr controller, JFrame frame, TreePanel treePanel) {
        this.controller = controller;
        this.frame = frame;
        this.treePanel = treePanel;
    }

    public void saveSelectedClass() {
        DefaultMutableTreeNode node = treePanel.getSelectedNode();
        if (node == null) {
            JOptionPane.showMessageDialog(frame, "No selection.", "Save Class", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ClassNode classNode = getSelectedClassNode(node);
        if (classNode == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Please select a class node to save.",
                "Save Class",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        String defaultName = classNode.name.substring(classNode.name.lastIndexOf('/') + 1) + ".class";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Class");
        chooser.setSelectedFile(new File(defaultName));
        int result = chooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            try {
                controller.jars().saveClass(classNode, file);
                controller.log("Saved class to " + file.getAbsolutePath());
            } catch (Exception ex) {
                controller.log("Save class failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "Save class failed: " + ex.getMessage(), "Save Class", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void saveSelectedJar() {
        String jarName = resolveSelectedJarName();
        if (jarName == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Please select a JAR group or a class within it.",
                "Save Selected JAR",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Selected JAR");
        chooser.setSelectedFile(new File(jarName));

        int result = chooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();

            if (!file.getName().toLowerCase().endsWith(".jar")) {
                file = new File(file.getParentFile(), file.getName() + ".jar");
            }

            try {
                controller.jars().exportJar(jarName, file);
                controller.renames().markExported(jarName);
                controller.log("Saved JAR to " + file.getAbsolutePath());
            } catch (Exception ex) {
                controller.log("Save JAR failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(frame, "Save failed: " + ex.getMessage(), "Save Selected JAR", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void overwriteOriginalJar() {
        String jarName = resolveSelectedJarName();
        if (jarName == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Please select a JAR group or a class within it.",
                "Overwrite Original JAR",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        File original = controller.jars().sourceFile(jarName);
        if (original == null) {
            JOptionPane.showMessageDialog(
                frame,
                "Original JAR path unknown for '" + jarName + "'. Try 'Export Selected JAR...' instead.",
                "Overwrite Original JAR",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            boolean ok = controller.jars().overwriteOriginalJar(jarName);

            if (ok) {
                controller.renames().commitOverwrittenJar(jarName);
                controller.log("Overwrote original JAR: " + original.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(
                    frame,
                    "Could not determine original JAR for '" + jarName + "'.",
                    "Overwrite Original JAR",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (Exception ex) {
            controller.log("Overwrite failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(
                frame,
                "Overwrite failed: " + ex.getMessage(),
                "Overwrite Original JAR",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private ClassNode getSelectedClassNode(DefaultMutableTreeNode node) {
        if (node instanceof ClassMutableTreeNode classMutableTreeNode) {
            return classMutableTreeNode.getClassNode();
        }
        if (node instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            return classNodeMutableTreeNode.getClassNode();
        }

        return null;
    }

    private String resolveSelectedJarName() {
        DefaultMutableTreeNode node = treePanel.getSelectedNode();

        switch (node) {
            case null -> {
                return null;
            }
            case JarMutableTreeNode jarMutableTreeNode -> {
                return jarMutableTreeNode.getJarName();
            }
            case ClassMutableTreeNode classMutableTreeNode -> {
                return classMutableTreeNode.getJarName();
            }
            case ClassNodeMutableTreeNode classNodeMutableTreeNode -> {
                return classNodeMutableTreeNode.getJarName();
            }
            default -> {
            }
        }

        javax.swing.tree.TreeNode parent = node.getParent();
        if (parent instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
            return classNodeMutableTreeNode.getJarName();
        }

        return null;
    }
}
