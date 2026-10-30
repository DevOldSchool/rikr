package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.SimilarClassReport;
import org.de.rikr.reference.ReferenceKind;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.ui.action.RenameMappingExport;
import org.de.rikr.ui.action.RenameMappingImport;
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
    private final JMenuItem exportRenameMappings = item("Export Rename Mappings...", this::exportRenameMappings);
    private final JMenuItem importRenameMappings = item("Import Rename Mappings...", this::importRenameMappings);
    private final JMenuItem findExtenders = item("Find Extenders", this::findExtenders);
    private final JMenuItem findImplementors = item("Find Implementors", this::findImplementors);
    private final JMenuItem findMatchingClassNodes = item("Find Matching Class Nodes", this::findMatchingClassNodes);
    private final JMenuItem getClassNodeSignature = item("Get Class Node Signature", this::showClassSignature);
    private final JMenuItem simulateMethod = item("Simulate Method", this::simulateMethod);
    private final JMenuItem showControlFlow = item("Show Control Flow", this::showControlFlow);
    private final JMenuItem showCallGraph = item("Show Call Graph", this::showCallGraph);
    private final JMenuItem showEntryPoints = item("Show Entry Points", this::showEntryPoints);
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
                add(showEntryPoints);
                add(importRenameMappings);
                add(exportRenameMappings);
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
                add(showControlFlow);
                add(showCallGraph);
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
            String jar = classNode.getJarName();
            String name = classNode.getClassNode().name;
            long mappings = controller.mappings().allAccepted().stream()
                .filter(mapping -> mapping.sourceJar().equals(jar) && mapping.sourceClassName().equals(name)
                    || mapping.targetJar().equals(jar) && mapping.targetClassName().equals(name))
                .count();
            if (!confirmRemove("Remove " + name + " from " + jar + "?",
                "Its " + plural(mappings, "accepted class mapping") + " " + (mappings == 1 ? "is" : "are")
                    + " discarded. The file on disk is not changed.")) {
                return;
            }
            model.removeNodeFromParent(node);
            controller.jars().removeClass(classNode.getJarName(), classNode.getClassNode());
            controller.getUserInterface().clearContent();
        } else if (node instanceof JarMutableTreeNode jarNode) {
            String jar = jarNode.getJarName();
            long renames = controller.renames().history().stream().filter(step -> step.jarName().equals(jar)).count();
            long mappings = controller.mappings().allAccepted().stream()
                .filter(mapping -> mapping.sourceJar().equals(jar) || mapping.targetJar().equals(jar))
                .count();
            long bookmarks = controller.bookmarks().all().stream()
                .filter(bookmark -> bookmark.jarName().equals(jar))
                .count();
            if (!confirmRemove("Remove " + jar + " from the project?",
                "Its " + plural(renames, "rename") + ", " + plural(mappings, "accepted class mapping") + " and "
                    + plural(bookmarks, "bookmark") + " are discarded, and its renames can no longer be undone. "
                    + "The file on disk is not changed.")) {
                return;
            }
            model.removeNodeFromParent(node);
            controller.jars().removeJar(jarNode.getJarName());
            controller.getUserInterface().clearContent();
        } else {
            controller.log("Unable to remove child node.");
        }
    }

    private boolean confirmRemove(String question, String consequence) {
        Object[] options = {"Remove", "Cancel"};
        int choice = JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor(tree),
            "<html><b>" + question + "</b><br><br>" + consequence + "</html>", "Remove",
            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return choice == 0;
    }

    private static String plural(long count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private void findExtenders(DefaultMutableTreeNode node) {
        if (node instanceof ClassNodeMutableTreeNode classNode) {
            ClassNode selected = classNode.getClassNode();
            List<ClassNode> classes = ClassNodeUtil.findClassesExtending(
                controller.jars().classes(classNode.getJarName()), selected);
            controller.log("Found " + classes.size() + " classes extending " + selected.name + ".");
            controller.getUserInterface().showClasses("Classes extending " + selected.name, classNode.getJarName(),
                classes, ReferenceKind.EXTENDS, "extends " + selected.name);
        }
    }

    private void findImplementors(DefaultMutableTreeNode node) {
        if (node instanceof InterfaceNodeMutableTreeNode interfaceNode) {
            ClassNode selected = interfaceNode.getClassNode();
            List<ClassNode> classes = ClassNodeUtil.findClassesImplementing(
                controller.jars().classes(interfaceNode.getJarName()), selected);
            controller.log("Found " + classes.size() + " classes implementing " + selected.name + ".");
            controller.getUserInterface().showClasses("Classes implementing " + selected.name,
                interfaceNode.getJarName(), classes, ReferenceKind.IMPLEMENTS, "implements " + selected.name);
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
            controller.getUserInterface().getTreePanel().updateTree(controller.jars().classesByJar(), false, false);
        }
    }

    private void exportRenameMappings(DefaultMutableTreeNode node) {
        if (node instanceof JarMutableTreeNode jarNode) {
            RenameMappingExport.export(controller, controller.getUserInterface().getFrame(), jarNode.getJarName());
        }
    }

    private void importRenameMappings(DefaultMutableTreeNode node) {
        if (node instanceof JarMutableTreeNode jarNode) {
            RenameMappingImport.importInto(controller, controller.getUserInterface().getFrame(), jarNode.getJarName());
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

    private void showControlFlow(DefaultMutableTreeNode node) {
        if (node instanceof MethodNodeMutableTreeNode methodNode) {
            controller.getUserInterface().showControlFlow(methodNode.getOwner(), methodNode.getMethodNode());
        }
    }

    private void showCallGraph(DefaultMutableTreeNode node) {
        if (node instanceof MethodNodeMutableTreeNode methodNode) {
            controller.getUserInterface().showCallGraph(methodNode.getOwner(), methodNode.getMethodNode());
        }
    }

    private void showEntryPoints(DefaultMutableTreeNode node) {
        if (node instanceof JarMutableTreeNode jarNode) {
            controller.getUserInterface().showEntryPoints(jarNode.getJarName());
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
                    SimilarClassReport report = get();
                    logReport(sourceClass, report);
                    controller.getUserInterface().showSimilarClasses(sourceJar, sourceClass, report);
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
