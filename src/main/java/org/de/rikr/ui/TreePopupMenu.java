package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.service.ClassMatch;
import org.de.rikr.ui.model.*;
import org.objectweb.asm.tree.*;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
    private final JMenuItem showReferences;

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
        showReferences = new JMenuItem("Show References");

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
                        add(showReferences);
                    } else if (selectedNode instanceof ClassNodeMutableTreeNode) {
                        add(findExtenders);
                        add(showReferences);
                    } else if (selectedNode instanceof InterfaceNodeMutableTreeNode) {
                        add(findImplementors);
                        add(showReferences);
                    } else if (selectedNode instanceof MethodNodeMutableTreeNode) {
                        add(simulateMethod);
                        add(showReferences);
                    } else if (selectedNode instanceof FieldNodeMutableTreeNode) {
                        add(showReferences);
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
                Map<String, List<ClassNode>> interfaceGroups = controller.groupByInterface(classes);

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
                boolean hasSuperClass = selectedClassNode.superName != null
                    && !"java/lang/Object".equals(selectedClassNode.superName);
                int interfaceCount = selectedClassNode.interfaces.size();

                List<String> fields = controller.getNonStaticFieldDescriptions(selectedClassNode);
                List<String> methods = controller.getNonStaticMethodDescriptions(selectedClassNode);

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
                findAndLogMatchingClassNodes(classMutableTreeNode);
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

        showReferences.addActionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }

            if (selectedNode instanceof ClassMutableTreeNode classNode) {
                controller.getUserInterface().showClassReferences(classNode.getJarName(), classNode.getClassNode());
            } else if (selectedNode instanceof ClassNodeMutableTreeNode classNode) {
                controller.getUserInterface().showClassReferences(classNode.getJarName(), classNode.getClassNode());
            } else if (selectedNode instanceof InterfaceNodeMutableTreeNode interfaceNode) {
                controller.getUserInterface().showClassReferences(
                    interfaceNode.getJarName(),
                    interfaceNode.getClassNode()
                );
            } else if (selectedNode instanceof FieldNodeMutableTreeNode fieldNode) {
                String jarName = getOwnerJarName((DefaultMutableTreeNode) fieldNode.getParent());
                controller.getUserInterface().showFieldReferences(
                    jarName,
                    fieldNode.getOwner(),
                    fieldNode.getFieldNode()
                );
            } else if (selectedNode instanceof MethodNodeMutableTreeNode methodNode) {
                String jarName = getOwnerJarName((DefaultMutableTreeNode) methodNode.getParent());
                controller.getUserInterface().showMethodReferences(
                    jarName,
                    methodNode.getOwner(),
                    methodNode.getMethodNode()
                );
            }
        });
    }

    private String getOwnerJarName(DefaultMutableTreeNode ownerNode) {
        if (ownerNode instanceof ClassNodeMutableTreeNode classNode) {
            return classNode.getJarName();
        }
        if (ownerNode instanceof InterfaceNodeMutableTreeNode interfaceNode) {
            return interfaceNode.getJarName();
        }
        throw new IllegalStateException("Reference owner is not attached to a loaded JAR");
    }

    private void findAndLogMatchingClassNodes(ClassMutableTreeNode selectedNode) {
        String sourceJarName = selectedNode.getJarName();
        ClassNode sourceClassNode = selectedNode.getClassNode();

        findMatchingClassNodes.setEnabled(false);
        controller.log("Finding class nodes similar to " + sourceClassNode.name + "...");

        new SwingWorker<MatchSearchReport, Void>() {
            @Override
            protected MatchSearchReport doInBackground() {
                return buildMatchSearchReport(sourceJarName, sourceClassNode);
            }

            @Override
            protected void done() {
                try {
                    MatchSearchReport report = get();
                    if (report.messages().isEmpty()) {
                        controller.log("Unable to find matching class nodes for " + sourceClassNode.name);
                    } else {
                        report.messages().forEach(controller::log);
                    }

                    if (report.skippedBehaviorComparisons() > 0) {
                        controller.log(String.format(
                            "Skipped %d behavioral method comparisons that could not be simulated.",
                            report.skippedBehaviorComparisons()
                        ));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    controller.log("Class node matching was interrupted for " + sourceClassNode.name);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String detail = cause == null ? exception.getMessage() : cause.getMessage();
                    controller.log("Unable to find matching class nodes for " + sourceClassNode.name + ": " + detail);
                } finally {
                    findMatchingClassNodes.setEnabled(true);
                }
            }
        }.execute();
    }

    private MatchSearchReport buildMatchSearchReport(String sourceJarName, ClassNode sourceClassNode) {
        Map<String, List<ClassNode>> jarClasses = controller.getJarClassesMap();
        List<ClassNode> sourceClasses = jarClasses.getOrDefault(sourceJarName, List.of());
        Map<String, List<ClassMatch>> jarMatches = controller.findSimilarClassNodes(sourceJarName, sourceClassNode);
        List<String> messages = new ArrayList<>();
        int skippedBehaviorComparisons = 0;

        for (Map.Entry<String, List<ClassMatch>> entry : jarMatches.entrySet()) {
            String jarName = entry.getKey();
            List<ClassNode> candidateClasses = jarClasses.getOrDefault(jarName, List.of());

            for (ClassMatch match : entry.getValue()) {
                StringBuilder message = new StringBuilder();
                message.append(String.format(
                    "Found %d%% structural match %s in jar %s",
                    match.similarityPercent(),
                    match.classNode().name,
                    jarName
                ));
                skippedBehaviorComparisons += appendEquivalentMethods(
                    message,
                    sourceClasses,
                    sourceClassNode,
                    candidateClasses,
                    match.classNode()
                );
                messages.add(message.toString());
            }
        }

        return new MatchSearchReport(List.copyOf(messages), skippedBehaviorComparisons);
    }

    private int appendEquivalentMethods(
        StringBuilder message,
        List<ClassNode> sourceClasses,
        ClassNode sourceClassNode,
        List<ClassNode> candidateClasses,
        ClassNode candidateClassNode
    ) {
        int skippedComparisons = 0;

        for (MethodNode sourceMethod : sourceClassNode.methods) {
            String sourceDescriptor = controller.normalizeClassDescriptor(sourceMethod.desc);
            for (MethodNode candidateMethod : candidateClassNode.methods) {
                if (!sourceDescriptor.equals(controller.normalizeClassDescriptor(candidateMethod.desc))) {
                    continue;
                }

                try {
                    if (controller.areMethodsBehaviorallyEquivalent(
                        sourceClasses,
                        sourceMethod,
                        candidateClasses,
                        candidateMethod
                    )) {
                        message.append(String.format(
                            "\n  - Behaviorally equivalent methods %s%s -> %s%s",
                            sourceMethod.name,
                            sourceMethod.desc,
                            candidateMethod.name,
                            candidateMethod.desc
                        ));
                    }
                } catch (RuntimeException exception) {
                    skippedComparisons++;
                }
            }
        }

        return skippedComparisons;
    }

    private record MatchSearchReport(List<String> messages, int skippedBehaviorComparisons) {
    }
}
