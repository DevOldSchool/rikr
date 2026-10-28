package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.model.ClassMappingDecision;
import org.de.rikr.service.ClassMatch;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MappingWorkspacePanel extends JPanel {
    private static final int DEFAULT_MINIMUM_SIMILARITY = 50;
    private static final int MAXIMUM_CANDIDATES = 100;

    private final Rikr controller;
    private final JComboBox<String> sourceJarBox = new JComboBox<>();
    private final JComboBox<String> targetJarBox = new JComboBox<>();
    private final JSpinner minimumSimilaritySpinner = new JSpinner(
        new SpinnerNumberModel(DEFAULT_MINIMUM_SIMILARITY, 0, 100, 5)
    );
    private final DefaultListModel<ClassNode> sourceClassModel = new DefaultListModel<>();
    private final DefaultListModel<ClassMatch> candidateModel = new DefaultListModel<>();
    private final JList<ClassNode> sourceClassList = new JList<>(sourceClassModel);
    private final JList<ClassMatch> candidateList = new JList<>(candidateModel);
    private final JTextArea sourceDetails = createDetailArea();
    private final JTextArea candidateDetails = createDetailArea();
    private final JButton acceptButton = new JButton("Accept");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton clearButton = new JButton("Clear source decisions");
    private final JButton autoMatchButton = new JButton("Auto-match unique");
    private final JButton exportButton = new JButton("Export JSON");
    private final JLabel statusLabel = new JLabel("Load at least two JARs to begin mapping.");
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel decisionBar = new JPanel(new FlowLayout(FlowLayout.LEFT));

    private boolean updatingJarChoices;
    private int candidateRequestSequence;

    public MappingWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        buildToolbar();
        buildWorkspace();
        configureRenderers();
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    public void init() {
        sourceJarBox.addActionListener(event -> jarSelectionChanged());
        targetJarBox.addActionListener(event -> jarSelectionChanged());
        minimumSimilaritySpinner.addChangeListener(event -> refreshCandidates());
        sourceClassList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                refreshCandidates();
            }
        });
        candidateList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateDetailsAndControls();
            }
        });
        acceptButton.addActionListener(event -> acceptSelectedCandidate());
        rejectButton.addActionListener(event -> rejectSelectedCandidate());
        clearButton.addActionListener(event -> clearSelectedSourceDecisions());
        autoMatchButton.addActionListener(event -> autoMatchUniqueCandidates());
        exportButton.addActionListener(event -> exportMappings());
        updateDetailsAndControls();
    }

    public void refreshJars(Map<String, List<ClassNode>> jarClasses) {
        String previousSource = selectedSourceJar();
        String previousTarget = selectedTargetJar();
        List<String> jarNames = jarClasses.keySet().stream().sorted().toList();

        updatingJarChoices = true;
        sourceJarBox.removeAllItems();
        targetJarBox.removeAllItems();
        jarNames.forEach(jarName -> {
            sourceJarBox.addItem(jarName);
            targetJarBox.addItem(jarName);
        });

        selectItem(sourceJarBox, previousSource, 0);
        int defaultTargetIndex = jarNames.size() > 1 ? 1 : 0;
        selectItem(targetJarBox, previousTarget, defaultTargetIndex);
        ensureDifferentJars();
        updatingJarChoices = false;

        reloadSourceClasses();
    }

    private void buildToolbar() {
        toolbar.add(new JLabel("Source JAR"));
        toolbar.add(sourceJarBox);
        toolbar.add(new JLabel("Target JAR"));
        toolbar.add(targetJarBox);
        toolbar.add(new JLabel("Minimum %"));
        toolbar.add(minimumSimilaritySpinner);
        toolbar.add(autoMatchButton);
        toolbar.add(exportButton);
        add(toolbar, BorderLayout.NORTH);
    }

    private void buildWorkspace() {
        sourceClassList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane sourceListScroll = titledScrollPane(sourceClassList, "Source classes");
        JScrollPane candidateListScroll = titledScrollPane(candidateList, "Ranked target candidates");
        JSplitPane lists = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceListScroll, candidateListScroll);
        lists.setResizeWeight(0.5);
        lists.setDividerSize(5);

        JScrollPane sourceDetailsScroll = titledScrollPane(sourceDetails, "Source details");
        JScrollPane candidateDetailsScroll = titledScrollPane(candidateDetails, "Candidate details");
        JSplitPane details = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceDetailsScroll, candidateDetailsScroll);
        details.setResizeWeight(0.5);
        details.setDividerSize(5);

        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lists, details);
        workspace.setResizeWeight(0.45);
        workspace.setDividerSize(5);
        add(workspace, BorderLayout.CENTER);

        decisionBar.add(acceptButton);
        decisionBar.add(rejectButton);
        decisionBar.add(clearButton);
        decisionBar.add(Box.createHorizontalStrut(8));
        decisionBar.add(statusLabel);
        add(decisionBar, BorderLayout.SOUTH);
    }

    private void configureRenderers() {
        sourceClassList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                );
                if (value instanceof ClassNode sourceClass) {
                    Optional<ClassMappingDecision> accepted = acceptedMapping(sourceClass);
                    String prefix = accepted.map(mapping -> "[Accepted " + mapping.similarityPercent() + "%] ")
                        .orElse("");
                    label.setText(prefix + sourceClass.name);
                }
                return label;
            }
        });

        candidateList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                );
                if (value instanceof ClassMatch candidate) {
                    String state = candidateState(candidate);
                    label.setText(candidate.similarityPercent() + "%  " + candidate.classNode().name + state);
                }
                return label;
            }
        });
    }

    private void jarSelectionChanged() {
        if (updatingJarChoices) {
            return;
        }
        updatingJarChoices = true;
        ensureDifferentJars();
        updatingJarChoices = false;
        reloadSourceClasses();
    }

    private void ensureDifferentJars() {
        String sourceJar = selectedSourceJar();
        String targetJar = selectedTargetJar();
        if (sourceJar == null || !sourceJar.equals(targetJar)) {
            return;
        }
        for (int index = 0; index < targetJarBox.getItemCount(); index++) {
            if (!sourceJar.equals(targetJarBox.getItemAt(index))) {
                targetJarBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private void reloadSourceClasses() {
        String sourceJar = selectedSourceJar();
        String previousClassName = Optional.ofNullable(sourceClassList.getSelectedValue())
            .map(classNode -> classNode.name)
            .orElse(null);
        sourceClassModel.clear();
        candidateModel.clear();

        if (!hasJarPair()) {
            sourceDetails.setText("");
            candidateDetails.setText("");
            updateDetailsAndControls();
            return;
        }

        List<ClassNode> classes = controller.jars().classes(sourceJar);
        if (classes != null) {
            classes.stream()
                .sorted(Comparator.comparing(classNode -> classNode.name))
                .forEach(sourceClassModel::addElement);
        }

        int selectionIndex = findSourceIndex(previousClassName);
        if (selectionIndex < 0 && !sourceClassModel.isEmpty()) {
            selectionIndex = 0;
        }
        if (selectionIndex >= 0) {
            sourceClassList.setSelectedIndex(selectionIndex);
        }
        updateDetailsAndControls();
    }

    private void refreshCandidates() {
        int request = ++candidateRequestSequence;
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        String targetJar = selectedTargetJar();
        candidateModel.clear();
        candidateDetails.setText("");
        if (!hasJarPair() || sourceClass == null || targetJar == null) {
            updateDetailsAndControls();
            return;
        }

        int minimumSimilarity = (Integer) minimumSimilaritySpinner.getValue();
        statusLabel.setText("Finding candidates for " + sourceClass.name + "...");
        candidateList.setEnabled(false);

        new SwingWorker<List<ClassMatch>, Void>() {
            @Override
            protected List<ClassMatch> doInBackground() {
                return controller.mappings().findCandidates(
                    targetJar,
                    sourceClass,
                    minimumSimilarity,
                    MAXIMUM_CANDIDATES
                );
            }

            @Override
            protected void done() {
                if (request != candidateRequestSequence) {
                    return;
                }
                candidateList.setEnabled(true);
                try {
                    get().forEach(candidateModel::addElement);
                    selectAcceptedCandidate(sourceClass);
                    updateDetailsAndControls();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Candidate search interrupted.");
                } catch (ExecutionException exception) {
                    showError("Candidate search failed", exception.getCause());
                }
            }
        }.execute();
    }

    private void selectAcceptedCandidate(ClassNode sourceClass) {
        Optional<ClassMappingDecision> accepted = acceptedMapping(sourceClass);
        if (accepted.isPresent()) {
            String targetName = accepted.get().targetClassName();
            for (int index = 0; index < candidateModel.size(); index++) {
                if (candidateModel.get(index).classNode().name.equals(targetName)) {
                    candidateList.setSelectedIndex(index);
                    return;
                }
            }
        }
        if (!candidateModel.isEmpty()) {
            candidateList.setSelectedIndex(0);
        }
    }

    private void acceptSelectedCandidate() {
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        ClassMatch candidate = candidateList.getSelectedValue();
        if (sourceClass == null || candidate == null || !hasJarPair()) {
            return;
        }

        try {
            controller.mappings().accept(selectedSourceJar(), selectedTargetJar(), sourceClass, candidate);
            statusLabel.setText("Accepted " + sourceClass.name + " -> " + candidate.classNode().name);
            repaintLists();
            updateDetailsAndControls();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(
                this,
                exception.getMessage(),
                "Mapping conflict",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void rejectSelectedCandidate() {
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        ClassMatch candidate = candidateList.getSelectedValue();
        if (sourceClass == null || candidate == null || !hasJarPair()) {
            return;
        }
        controller.mappings().reject(
            selectedSourceJar(),
            selectedTargetJar(),
            sourceClass,
            candidate.classNode()
        );
        statusLabel.setText("Rejected " + sourceClass.name + " -> " + candidate.classNode().name);
        repaintLists();
        updateDetailsAndControls();
    }

    private void clearSelectedSourceDecisions() {
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        if (sourceClass == null || !hasJarPair()) {
            return;
        }
        controller.mappings().clearDecisions(selectedSourceJar(), selectedTargetJar(), sourceClass);
        statusLabel.setText("Cleared decisions for " + sourceClass.name);
        repaintLists();
        updateDetailsAndControls();
    }

    private void autoMatchUniqueCandidates() {
        if (!hasJarPair()) {
            return;
        }
        String sourceJar = selectedSourceJar();
        String targetJar = selectedTargetJar();
        int minimumSimilarity = (Integer) minimumSimilaritySpinner.getValue();
        autoMatchButton.setEnabled(false);
        statusLabel.setText("Auto-matching unique candidates...");

        new SwingWorker<List<ClassMappingDecision>, Void>() {
            @Override
            protected List<ClassMappingDecision> doInBackground() {
                return controller.mappings().autoAcceptUnique(sourceJar, targetJar, minimumSimilarity);
            }

            @Override
            protected void done() {
                autoMatchButton.setEnabled(true);
                try {
                    int added = get().size();
                    statusLabel.setText("Auto-matched " + added + " unique class" + (added == 1 ? "." : "es."));
                    repaintLists();
                    refreshCandidates();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Auto-match interrupted.");
                } catch (ExecutionException exception) {
                    showError("Auto-match failed", exception.getCause());
                }
            }
        }.execute();
    }

    private void exportMappings() {
        if (!hasJarPair()) {
            return;
        }
        String sourceJar = selectedSourceJar();
        String targetJar = selectedTargetJar();
        List<ClassMappingDecision> mappings = controller.mappings().accepted(sourceJar, targetJar);
        if (mappings.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Accept at least one mapping before exporting.",
                "Export mappings",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(exportFileName(sourceJar, targetJar)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith(".json")) {
            destination = new File(destination.getParentFile(), destination.getName() + ".json");
        }
        if (destination.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(
                this,
                "Replace " + destination.getName() + "?",
                "Export mappings",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            controller.mappings().export(destination, sourceJar, targetJar);
            statusLabel.setText("Exported " + mappings.size() + " mappings to " + destination.getName());
        } catch (IOException exception) {
            showError("Export failed", exception);
        }
    }

    private void updateDetailsAndControls() {
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        ClassMatch candidate = candidateList.getSelectedValue();
        sourceDetails.setText(describeClass(sourceClass, null));
        candidateDetails.setText(describeClass(
            candidate == null ? null : candidate.classNode(),
            candidate == null ? null : candidate.similarityPercent()
        ));
        sourceDetails.setCaretPosition(0);
        candidateDetails.setCaretPosition(0);

        boolean hasSource = hasJarPair() && sourceClass != null;
        boolean hasCandidate = hasSource && candidate != null;
        acceptButton.setEnabled(hasCandidate);
        rejectButton.setEnabled(hasCandidate);
        clearButton.setEnabled(hasSource);
        autoMatchButton.setEnabled(hasJarPair());
        exportButton.setEnabled(hasJarPair());

        if (!hasJarPair()) {
            statusLabel.setText("Load at least two JARs to begin mapping.");
        } else if (sourceClass == null) {
            statusLabel.setText("Select a source class.");
        } else if (candidateModel.isEmpty() && candidateList.isEnabled()) {
            statusLabel.setText("No candidates meet the minimum similarity.");
        } else {
            Optional<ClassMappingDecision> accepted = acceptedMapping(sourceClass);
            accepted.ifPresentOrElse(
                mapping -> statusLabel.setText(
                    "Accepted: " + mapping.sourceClassName() + " -> " + mapping.targetClassName()
                        + " (" + mapping.similarityPercent() + "%)"
                ),
                () -> statusLabel.setText("Select a candidate to accept or reject.")
            );
        }
    }

    private Optional<ClassMappingDecision> acceptedMapping(ClassNode sourceClass) {
        if (sourceClass == null || !hasJarPair()) {
            return Optional.empty();
        }
        return controller.mappings().accepted(selectedSourceJar(), selectedTargetJar(), sourceClass);
    }

    private String candidateState(ClassMatch candidate) {
        ClassNode sourceClass = sourceClassList.getSelectedValue();
        if (sourceClass == null || !hasJarPair()) {
            return "";
        }
        Optional<ClassMappingDecision> accepted = acceptedMapping(sourceClass);
        if (accepted.isPresent() && accepted.get().targetClassName().equals(candidate.classNode().name)) {
            return "  [Accepted]";
        }
        if (controller.mappings().isRejected(
            selectedSourceJar(),
            selectedTargetJar(),
            sourceClass,
            candidate.classNode()
        )) {
            return "  [Rejected]";
        }
        return "";
    }

    private String describeClass(ClassNode classNode, Integer similarityPercent) {
        if (classNode == null) {
            return "";
        }
        StringBuilder details = new StringBuilder();
        if (similarityPercent != null) {
            details.append("Similarity: ").append(similarityPercent).append("%\n");
        }
        details.append("Name: ").append(classNode.name).append('\n');
        details.append("Kind: ").append(classKind(classNode)).append('\n');
        details.append("Superclass: ").append(classNode.superName == null ? "<none>" : classNode.superName).append('\n');
        details.append("Interfaces (").append(classNode.interfaces.size()).append("):\n");
        classNode.interfaces.forEach(name -> details.append("  ").append(name).append('\n'));
        details.append("\nFields (").append(classNode.fields.size()).append("):\n");
        for (FieldNode field : classNode.fields) {
            details.append("  ");
            if (Modifier.isStatic(field.access)) {
                details.append("static ");
            }
            details.append(field.name).append(' ').append(field.desc).append('\n');
        }
        details.append("\nMethods (").append(classNode.methods.size()).append("):\n");
        for (MethodNode method : classNode.methods) {
            details.append("  ");
            if (Modifier.isStatic(method.access)) {
                details.append("static ");
            }
            details.append(method.name).append(method.desc).append('\n');
        }
        return details.toString();
    }

    private String classKind(ClassNode classNode) {
        if (Modifier.isInterface(classNode.access)) {
            return "interface";
        }
        if (Modifier.isAbstract(classNode.access)) {
            return "abstract class";
        }
        return "class";
    }

    private void repaintLists() {
        sourceClassList.repaint();
        candidateList.repaint();
    }

    private boolean hasJarPair() {
        String sourceJar = selectedSourceJar();
        String targetJar = selectedTargetJar();
        return sourceJar != null && targetJar != null && !sourceJar.equals(targetJar);
    }

    private String selectedSourceJar() {
        return (String) sourceJarBox.getSelectedItem();
    }

    private String selectedTargetJar() {
        return (String) targetJarBox.getSelectedItem();
    }

    private int findSourceIndex(String className) {
        if (className == null) {
            return -1;
        }
        for (int index = 0; index < sourceClassModel.size(); index++) {
            if (sourceClassModel.get(index).name.equals(className)) {
                return index;
            }
        }
        return -1;
    }

    private void selectItem(JComboBox<String> comboBox, String preferred, int defaultIndex) {
        if (preferred != null) {
            comboBox.setSelectedItem(preferred);
        }
        if (comboBox.getSelectedIndex() < 0 && comboBox.getItemCount() > defaultIndex) {
            comboBox.setSelectedIndex(defaultIndex);
        }
    }

    private String exportFileName(String sourceJar, String targetJar) {
        return stripJarSuffix(sourceJar) + "-to-" + stripJarSuffix(targetJar) + "-mappings.json";
    }

    private String stripJarSuffix(String name) {
        return name.toLowerCase().endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }

    private void showError(String title, Throwable throwable) {
        String detail = throwable == null ? "Unknown error" : throwable.getMessage();
        statusLabel.setText(title + ": " + detail);
        JOptionPane.showMessageDialog(this, detail, title, JOptionPane.ERROR_MESSAGE);
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        toolbar.setBackground(Theme.BACKGROUND_COLOR);
        decisionBar.setBackground(Theme.BACKGROUND_COLOR);
        sourceClassList.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        candidateList.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        sourceDetails.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        candidateDetails.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        revalidate();
        repaint();
    }

    private static JScrollPane titledScrollPane(Component component, String title) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(BorderFactory.createTitledBorder(title));
        return scrollPane;
    }

    private static JTextArea createDetailArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        return area;
    }
}
