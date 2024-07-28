package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
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
    private final JMenuItem findMatchingClassNodes;
    private final JMenuItem getClassNodeSignature;
    private final JMenuItem simulateMethod;

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
        findMatchingClassNodes = new JMenuItem("Find Matching Class Nodes");
        getClassNodeSignature = new JMenuItem("Get Class Node Signature");
        simulateMethod = new JMenuItem("Simulate Method");

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
                    } else if (selectedNode instanceof ClassMutableTreeNode) {
                        add(getClassNodeSignature);
                        add(findMatchingClassNodes);
                    } else if (selectedNode instanceof ClassNodeMutableTreeNode) {
                        add(findExtenders);
                    } else if (selectedNode instanceof InterfaceNodeMutableTreeNode) {
                        add(findImplementors);
                    } else if (selectedNode instanceof MethodNodeMutableTreeNode) {
                        add(simulateMethod);
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

        renameItem.addActionListener(controller.getUserInterface().getContentPanel().getRenameActionHandler());

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
                    controller.getUserInterface().clearContent();
                } else {
                    model.removeNodeFromParent(selectedNode);
                    controller.removeJar(selectedNode.toString());
                    controller.getUserInterface().clearContent();
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
                List<ClassNode> classes = controller.getProcessor().findClassesExtending(controller.getClasses(jarName), selectedClassNode);

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
                List<ClassNode> classes = controller.getProcessor().findClassesImplementing(controller.getClasses(jarName), selectedClassNode);

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
                Map<String, List<ClassNode>> superclassGroups = controller.getProcessor().groupBySuperclass(classes);

                controller.getUserInterface().getTreePanel().addTreeNodes(selectedNode, jarName, classes, superclassGroups);
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
                Map<String, List<ClassNode>> interfaceGroups = controller.getProcessor().groupByInterface(classes);

                controller.getUserInterface().getTreePanel().addTreeNodes(selectedNode, jarName, classes, interfaceGroups);
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

                controller.getUserInterface().getTreePanel().updateNode(selectedNode);
            }
        });

        getClassNodeSignature.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                ClassNode selectedClassNode = classMutableTreeNode.getClassNode();
                Map<String, String> informationMap = new LinkedHashMap<>();

                boolean isInterface = Modifier.isInterface(selectedClassNode.access);
                boolean isAbstract = Modifier.isAbstract(selectedClassNode.access);
                boolean hasSuperClass = !selectedClassNode.superName.equals("java/lang/Object");
                int interfaceCount = selectedClassNode.interfaces.size();

                List<String> fields = controller.getProcessor().getNonStaticFieldDescriptions(selectedClassNode);
                List<String> methods = controller.getProcessor().getNonStaticMethodDescriptions(selectedClassNode);

                informationMap.put("ClassNode", selectedClassNode.name);
                informationMap.put("Is Interface", String.valueOf(isInterface));
                informationMap.put("Is Abstract", String.valueOf(isAbstract));
                informationMap.put("Has Superclass", String.valueOf(hasSuperClass));
                informationMap.put("Interface count", String.valueOf(interfaceCount));
                informationMap.put("Field count", String.valueOf(fields.size()));
                informationMap.put("Method count", String.valueOf(methods.size()));

                new InformationFrame(controller, informationMap);
            }
        });

        findMatchingClassNodes.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof ClassMutableTreeNode classMutableTreeNode) {
                String jarToExclude = classMutableTreeNode.getJarName();
                ClassNode selectedClassNode = classMutableTreeNode.getClassNode();
                List<ClassNode> selectedClassNodes = controller.getClasses(classMutableTreeNode.getJarName());
                Map<String, List<ClassNode>> jarMatchingMap = controller.getProcessor().findMatchingClassNodes(jarToExclude, selectedClassNode);

                boolean foundMatch = false;
                for (String jarName : jarMatchingMap.keySet()) {
                    List<ClassNode> compareClassNodes = controller.getClasses(jarName);

                    for (ClassNode classNode : jarMatchingMap.get(jarName)) {
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(String.format("Found matching class node %s in jar %s", classNode.name, jarName));
                        foundMatch = true;

                        for (MethodNode methodNode1 : selectedClassNode.methods) {
                            for (MethodNode methodNode2 : classNode.methods) {
                                if (!methodNode1.desc.replaceAll("L.*?;", "L?;").equals(methodNode2.desc.replaceAll("L.*?;", "L?;"))) {
                                    continue;
                                }

                                if (controller.getProcessor().areMethodsBehaviorallyEquivalent(selectedClassNodes, methodNode1, compareClassNodes, methodNode2)) {
                                    stringBuilder.append(String.format("\n  - Methods are behaviorally equivalent %s -> %s", methodNode1.name + methodNode1.desc, methodNode2.name + methodNode2.desc));
                                }
                            }
                        }

                        controller.log(stringBuilder.toString());
                    }
                }

                if (!foundMatch) {
                    controller.log(String.format("Unable to find matching class node for %s", selectedClassNode.name));
                }
            }
        });

        simulateMethod.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof MethodNodeMutableTreeNode methodNodeMutableTreeNode) {
                ClassNodeMutableTreeNode parentNode = (ClassNodeMutableTreeNode) methodNodeMutableTreeNode.getParent();
                MethodNode methodNode = methodNodeMutableTreeNode.getMethodNode();

                controller.getUserInterface().getMethodSimulatorPanel().initSimulate(parentNode, methodNode);
            }
        });
    }
}
