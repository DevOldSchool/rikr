package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.SimilarClassReport;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.ui.model.*;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The project tree's context menu. Which items it shows depends on the kind of node that is selected.
 */
public class TreePopupMenu extends JPopupMenu {
    private final Rikr controller;
    private final JTree tree;
    private final JMenuItem renameItem = new JMenuItem("Rename");
    private final JMenuItem removeItem = item("Remove", this::remove);
    private final JMenuItem groupBySuperclass = item("Group by Superclass",
        node -> group(node, ClassNodeUtil::groupBySuperclass));
    private final JMenuItem groupByInterface = item("Group by Interface",
        node -> group(node, ClassNodeUtil::groupByInterface));
    private final JMenuItem removeGrouping = item("Remove Grouping", this::removeGrouping);
    private final JMenuItem findExtenders = item("Find Extenders", this::findExtenders);
    private final JMenuItem findImplementors = item("Find Implementors", this::findImplementors);
    private final JMenuItem findMatchingClassNodes = item("Find Matching Class Nodes", this::findMatchingClassNodes);
    private final JMenuItem getClassNodeSignature = item("Get Class Node Signature", this::showClassSignature);
    private final JMenuItem simulateMethod = item("Simulate Method", this::simulateMethod);
    private final JMenuItem showReferences = item("Show References", this::showReferences);
    private final JMenuItem toggleBookmark = item("Toggle Bookmark", this::toggleBookmark);

    public TreePopupMenu(Rikr controller, JTree tree) {
        this.controller = controller;
        this.tree = tree;
        add(renameItem);
        add(removeItem);
    }

    public void init() {
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                DefaultMutableTreeNode node = selectedNode();
                if (node != null && node.getParent() != null) {
                    rebuild(node);
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
    }

    private void rebuild(DefaultMutableTreeNode node) {
        removeAll();
        add(renameItem);
        if (node instanceof ClassMutableTreeNode || node instanceof JarMutableTreeNode) {
            add(removeItem);
        }

        switch (node) {
            case JarMutableTreeNode ignored -> {
                add(groupBySuperclass);
                add(groupByInterface);
                add(removeGrouping);
            }
            case ClassMutableTreeNode ignored -> {
                add(getClassNodeSignature);
                add(findMatchingClassNodes);
                add(showReferences);
            }
            case ClassNodeMutableTreeNode ignored -> {
                add(findExtenders);
                add(showReferences);
            }
            case InterfaceNodeMutableTreeNode ignored -> {
                add(findImplementors);
                add(showReferences);
            }
            case MethodNodeMutableTreeNode ignored -> {
                add(simulateMethod);
                add(showReferences);
            }
            case FieldNodeMutableTreeNode ignored -> add(showReferences);
            default -> {
            }
        }
        if (TreeNodes.declarationOf(node).isPresent()) {
            add(toggleBookmark);
        }
    }

    private void toggleBookmark(DefaultMutableTreeNode node) {
        TreeNodes.bookmarkOf(node).ifPresent(controller::toggleBookmark);
    }

    private void remove(DefaultMutableTreeNode node) {
        if (node.getParent() == null) {
            return;
        }
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        if (node instanceof ClassMutableTreeNode classNode) {
            model.removeNodeFromParent(node);
            controller.jars().removeClass(classNode.getJarName(), classNode.getClassNode());
            controller.getUserInterface().clearContent();
        } else if (node instanceof JarMutableTreeNode jarNode) {
            model.removeNodeFromParent(node);
            controller.jars().removeJar(jarNode.getJarName());
            controller.getUserInterface().clearContent();
        } else {
            controller.log("Unable to remove child node.");
        }
    }

    private void findExtenders(DefaultMutableTreeNode node) {
        if (node instanceof ClassNodeMutableTreeNode classNode) {
            ClassNode selected = classNode.getClassNode();
            List<ClassNode> classes = ClassNodeUtil.findClassesExtending(
                controller.jars().classes(classNode.getJarName()), selected);
            classes.forEach(found -> controller.log(String.format("Found class %s extends %s", found.name, selected.name)));
            if (classes.isEmpty()) {
                controller.log(String.format("Unable to find extender class for %s", selected.name));
            }
        }
    }

    private void findImplementors(DefaultMutableTreeNode node) {
        if (node instanceof InterfaceNodeMutableTreeNode interfaceNode) {
            ClassNode selected = interfaceNode.getClassNode();
            List<ClassNode> classes = ClassNodeUtil.findClassesImplementing(
                controller.jars().classes(interfaceNode.getJarName()), selected);
            classes.forEach(found -> controller.log(String.format("Found class %s implementing %s", found.name, selected.name)));
            if (classes.isEmpty()) {
                controller.log(String.format("Unable to find implementor class for %s", selected.name));
            }
        }
    }

    private void group(DefaultMutableTreeNode node, Function<List<ClassNode>, Map<String, List<ClassNode>>> grouping) {
        if (node instanceof JarMutableTreeNode jarNode) {
            List<ClassNode> classes = jarNode.getClasses();
            controller.getUserInterface().getTreePanel()
                .addTreeNodes(node, jarNode.getJarName(), classes, grouping.apply(classes));
        }
    }

    private void removeGrouping(DefaultMutableTreeNode node) {
        if (node instanceof JarMutableTreeNode jarNode) {
            node.removeAllChildren();
            for (ClassNode classNode : jarNode.getClasses()) {
                node.add(new ClassMutableTreeNode(jarNode.getJarName(), classNode, classNode.name + ".class"));
            }
            controller.getUserInterface().getTreePanel().updateNode(node);
        }
    }

    private void showClassSignature(DefaultMutableTreeNode node) {
        if (!(node instanceof ClassMutableTreeNode classNode)) {
            return;
        }
        ClassNode selected = classNode.getClassNode();
        Map<String, String> information = new LinkedHashMap<>();
        information.put("ClassNode", selected.name);
        information.put("Is Interface", String.valueOf(Modifier.isInterface(selected.access)));
        information.put("Is Abstract", String.valueOf(Modifier.isAbstract(selected.access)));
        information.put("Has Superclass", String.valueOf(
            selected.superName != null && !"java/lang/Object".equals(selected.superName)));
        information.put("Interface count", String.valueOf(selected.interfaces.size()));
        information.put("Field count", String.valueOf(controller.mappings().nonStaticFieldDescriptions(selected).size()));
        information.put("Method count", String.valueOf(controller.mappings().nonStaticMethodDescriptions(selected).size()));
        new InformationFrame(controller, information);
    }

    private void simulateMethod(DefaultMutableTreeNode node) {
        if (node instanceof MethodNodeMutableTreeNode methodNode) {
            ClassNodeMutableTreeNode parent = (ClassNodeMutableTreeNode) methodNode.getParent();
            controller.getUserInterface().getMethodSimulatorPanel().initSimulate(parent, methodNode.getMethodNode());
        }
    }

    private void showReferences(DefaultMutableTreeNode node) {
        Optional<String> jar = TreeNodes.jarOf(node);
        Optional<Declaration> declaration = TreeNodes.declarationOf(node);
        if (jar.isEmpty() || declaration.isEmpty()) {
            return;
        }
        var userInterface = controller.getUserInterface();
        switch (declaration.get()) {
            case Declaration.ClassDeclaration target -> userInterface.showClassReferences(jar.get(), target.owner());
            case Declaration.FieldDeclaration target ->
                userInterface.showFieldReferences(jar.get(), target.owner(), target.field());
            case Declaration.MethodDeclaration target ->
                userInterface.showMethodReferences(jar.get(), target.owner(), target.method());
        }
    }

    private void findMatchingClassNodes(DefaultMutableTreeNode node) {
        if (!(node instanceof ClassMutableTreeNode classNode)) {
            return;
        }
        String sourceJar = classNode.getJarName();
        ClassNode sourceClass = classNode.getClassNode();
        findMatchingClassNodes.setEnabled(false);
        controller.log("Finding class nodes similar to " + sourceClass.name + "...");

        new SwingWorker<SimilarClassReport, Void>() {
            @Override
            protected SimilarClassReport doInBackground() {
                return controller.mappings().similarClassReport(sourceJar, sourceClass);
            }

            @Override
            protected void done() {
                try {
                    logReport(sourceClass, get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    controller.log("Class node matching was interrupted for " + sourceClass.name);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String detail = cause == null ? exception.getMessage() : cause.getMessage();
                    controller.log("Unable to find matching class nodes for " + sourceClass.name + ": " + detail);
                } finally {
                    findMatchingClassNodes.setEnabled(true);
                }
            }
        }.execute();
    }

    private void logReport(ClassNode sourceClass, SimilarClassReport report) {
        if (report.matches().isEmpty()) {
            controller.log("Unable to find matching class nodes for " + sourceClass.name);
        }
        for (SimilarClassReport.Match match : report.matches()) {
            StringBuilder message = new StringBuilder(String.format(
                "Found %d%% structural match %s in jar %s",
                match.match().similarityPercent(),
                match.match().classNode().name,
                match.jar()
            ));
            for (SimilarClassReport.MethodPair pair : match.equivalentMethods()) {
                message.append(String.format(
                    "\n  - Behaviorally equivalent methods %s%s -> %s%s",
                    pair.source().name,
                    pair.source().desc,
                    pair.target().name,
                    pair.target().desc
                ));
            }
            controller.log(message.toString());
        }
        if (report.skippedComparisons() > 0) {
            controller.log(String.format(
                "Skipped %d behavioral method comparisons that could not be simulated.",
                report.skippedComparisons()
            ));
        }
    }

    private DefaultMutableTreeNode selectedNode() {
        return (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    }

    /**
     * A menu item that runs its action on the selected node, if there is one.
     */
    private JMenuItem item(String label, Consumer<DefaultMutableTreeNode> action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> {
            DefaultMutableTreeNode node = selectedNode();
            if (node != null) {
                action.accept(node);
            }
        });
        return item;
    }
}
