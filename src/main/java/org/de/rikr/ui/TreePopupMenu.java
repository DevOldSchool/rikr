package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.Map;

public class TreePopupMenu extends JPopupMenu {
    private final Rikr controller;
    private final JTree tree;
    private final JMenuItem removeItem;
    private final JMenuItem renameItem;
    private final JMenuItem groupBySuperclass;
    private final JMenuItem groupByInterface;
    private final JMenuItem removeGrouping;
    private final JMenuItem findExtenders;
    private final JMenuItem findImplementors;

    public TreePopupMenu(Rikr controller, JTree tree) {
        this.controller = controller;
        this.tree = tree;

        renameItem = new JMenuItem("Rename");
        removeItem = new JMenuItem("Remove");
        groupBySuperclass = new JMenuItem("Group by Superclass");
        groupByInterface = new JMenuItem("Group by Interface");
        removeGrouping = new JMenuItem("Remove Grouping");
        findExtenders = new JMenuItem("Find Extenders");
        findImplementors = new JMenuItem("Find Implementors");

        add(renameItem);
        add(removeItem);
    }

    public void init() {
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (selectedNode != null && selectedNode.getParent() != null) {
                    boolean canRemoveNode = !(selectedNode instanceof ClassNodeMutableTreeNode ||
                            selectedNode instanceof InterfaceNodeMutableTreeNode ||
                            selectedNode instanceof FieldNodeMutableTreeNode ||
                            selectedNode instanceof MethodNodeMutableTreeNode);

                    removeAll();

                    add(renameItem);

                    if (canRemoveNode) {
                        add(removeItem);
                    }

                    if (selectedNode instanceof JarMutableTreeNode) {
                        add(groupBySuperclass);
                        add(groupByInterface);
                        add(removeGrouping);
                    } else if (selectedNode instanceof ClassNodeMutableTreeNode) {
                        add(findExtenders);
                    } else if (selectedNode instanceof InterfaceNodeMutableTreeNode) {
                        add(findImplementors);
                    }
                }
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        renameItem.addActionListener(controller.getContentPanel().getRenameActionHandler());

        removeItem.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode != null && selectedNode.getParent() != null) {
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

                if (selectedNode instanceof ClassNodeMutableTreeNode ||
                        selectedNode instanceof InterfaceNodeMutableTreeNode ||
                        selectedNode instanceof FieldNodeMutableTreeNode ||
                        selectedNode instanceof MethodNodeMutableTreeNode) {
                    controller.log("Unable to remove child node.");
                } else if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                    model.removeNodeFromParent(selectedNode);
                    controller.removeClass(classMutableTreeNode.getJarName(), classMutableTreeNode.getClassNode());
                    controller.clearContent();
                } else {
                    model.removeNodeFromParent(selectedNode);
                    controller.removeJar(selectedNode.toString());
                    controller.clearContent();
                }
            }
        });

        findExtenders.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof ClassNodeMutableTreeNode classNodeMutableTreeNode) {
                String jarName = classNodeMutableTreeNode.getJarName();
                ClassNode selectedClassNode = classNodeMutableTreeNode.getClassNode();
                List<ClassNode> classes = controller.findClassesExtending(controller.getClasses(jarName), selectedClassNode);

                boolean foundMatch = false;
                for (ClassNode classNode : classes) {
                    controller.log(String.format("Found class %s extends %s", classNode.name, selectedClassNode.name));
                    foundMatch = true;
                }

                if (!foundMatch) {
                    controller.log(String.format("Unable to find extender class for %s", selectedClassNode.name));
                }
            }
        });

        findImplementors.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNodeMutableTreeNode) {
                String jarName = interfaceNodeMutableTreeNode.getJarName();
                ClassNode selectedClassNode = interfaceNodeMutableTreeNode.getClassNode();
                List<ClassNode> classes = controller.findClassesImplementing(controller.getClasses(jarName), selectedClassNode);

                boolean foundMatch = false;
                for (ClassNode classNode : classes) {
                    controller.log(String.format("Found class %s implementing %s", classNode.name, selectedClassNode.name));
                    foundMatch = true;
                }

                if (!foundMatch) {
                    controller.log(String.format("Unable to find implementor class for %s", selectedClassNode.name));
                }
            }
        });

        groupBySuperclass.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof JarMutableTreeNode jarMutableTreeNode) {
                String jarName = jarMutableTreeNode.getJarName();
                List<ClassNode> classes = jarMutableTreeNode.getClasses();
                Map<String, List<ClassNode>> superclassGroups = controller.groupBySuperclass(classes);

                controller.getTreePanel().addTreeNodes(selectedNode, jarName, classes, superclassGroups);
            }
        });

        groupByInterface.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof JarMutableTreeNode jarMutableTreeNode) {
                String jarName = jarMutableTreeNode.getJarName();
                List<ClassNode> classes = jarMutableTreeNode.getClasses();
                Map<String, List<ClassNode>> interfaceGroups = controller.groupByInterface(classes);

                controller.getTreePanel().addTreeNodes(selectedNode, jarName, classes, interfaceGroups);
            }
        });

        removeGrouping.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof JarMutableTreeNode jarMutableTreeNode) {
                selectedNode.removeAllChildren();
                String jarName = jarMutableTreeNode.getJarName();
                List<ClassNode> classes = jarMutableTreeNode.getClasses();

                for (ClassNode classNode : classes) {
                    String className = classNode.name + ".class";
                    selectedNode.add(new ClassMutableTreeNode(jarName, classNode, className));
                }

                controller.getTreePanel().updateNode(selectedNode);
            }
        });
    }
}
