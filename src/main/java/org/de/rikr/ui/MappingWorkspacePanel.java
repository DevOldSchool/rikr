package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MappingWorkspacePanel extends JPanel {
    private static final int DEFAULT_MINIMUM_SIMILARITY = 50;
    private static final int MAXIMUM_CANDIDATES = 100;
    private static final String SHOW_ALL = "All classes";
    private static final String SHOW_UNMAPPED = "Unmapped";
    private static final String SHOW_MAPPED = "Mapped";

    private final Rikr controller;
    private final JarPairSelector jars = new JarPairSelector(this::reloadSourceClasses);
    private final JSpinner minimumSimilaritySpinner = new JSpinner(
        new SpinnerNumberModel(DEFAULT_MINIMUM_SIMILARITY, 0, 100, 5)
    );
    private final DefaultListModel<ClassNode> sourceClassModel = new DefaultListModel<>();
    private final DefaultListModel<ClassMatch> candidateModel = new DefaultListModel<>();
    private final JList<ClassNode> sourceClassList = new JList<>(sourceClassModel);
    private final JList<ClassMatch> candidateList = new JList<>(candidateModel);
    private final JTextArea sourceDetails = createDetailArea();
    private final JTextArea candidateDetails = createDetailArea();
    private final JButton acceptButton = new JButton("Accept ↩");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton clearButton = new JButton("Clear source decisions");
    private final JButton autoMatchButton = new JButton("Auto-match unique");
    private final JButton exportButton = new JButton("Export JSON");
    private final JLabel statusLabel = new JLabel("Load at least two JARs to begin mapping.");
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel decisionBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final List<ClassNode> allSourceClasses = new ArrayList<>();
    private final JTextField filterField = new JTextField(12);
    private final JComboBox<String> showBox = new JComboBox<>(new String[]{SHOW_ALL, SHOW_UNMAPPED, SHOW_MAPPED});
    private final JLabel progressLabel = new JLabel(" ");
    private final JLabel differenceLegend = new JLabel(
        "Highlighted members have no member of the same shape in the other class.");

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
        jars.init();
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
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                applyFilter();
            }
        });
        showBox.addActionListener(event -> applyFilter());
        // Decide from the keyboard: Enter accepts and moves to the next unmapped class, Delete rejects.
        for (JList<?> list : List.of(sourceClassList, candidateList)) {
            bind(list, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "accept",
                this::acceptSelectedCandidate);
            bind(list, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0), "reject",
                this::rejectSelectedCandidate);
            bind(list, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_BACK_SPACE, 0), "reject",
                this::rejectSelectedCandidate);
        }
        clearButton.addActionListener(event -> clearSelectedSourceDecisions());
        autoMatchButton.addActionListener(event -> autoMatchUniqueCandidates());
        exportButton.addActionListener(event -> exportMappings());
        updateDetailsAndControls();
    }

    public void refreshJars(Map<String, List<ClassNode>> jarClasses) {
        jars.setJars(jarClasses.keySet().stream().sorted().toList());
        reloadSourceClasses();
    }

    private void buildToolbar() {
        toolbar.add(new JLabel("Source JAR"));
        toolbar.add(jars.sourceBox());
        toolbar.add(new JLabel("Target JAR"));
        toolbar.add(jars.targetBox());
        toolbar.add(new JLabel("Minimum %"));
        toolbar.add(minimumSimilaritySpinner);
        toolbar.add(autoMatchButton);
        toolbar.add(exportButton);
        add(toolbar, BorderLayout.NORTH);
    }

    private static void bind(JComponent component, KeyStroke key, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    private void buildWorkspace() {
        sourceClassList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceClassList.getAccessibleContext().setAccessibleName("Source classes");
        candidateList.getAccessibleContext().setAccessibleName("Ranked target candidates");

        JScrollPane sourceListScroll = titledScrollPane(sourceClassList, "Source classes");
        SearchFields.style(filterField, "Filter classes");
        showBox.setToolTipText("Show all source classes, or only those not yet mapped, or only mapped ones");
        JPanel sourceFilter = new JPanel(new BorderLayout(6, 0));
        sourceFilter.setOpaque(false);
        sourceFilter.add(filterField, BorderLayout.CENTER);
        sourceFilter.add(showBox, BorderLayout.EAST);
        sourceFilter.add(progressLabel, BorderLayout.SOUTH);
        progressLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        JPanel sourcePane = new JPanel(new BorderLayout(0, 4));
        sourcePane.setOpaque(false);
        sourcePane.add(sourceFilter, BorderLayout.NORTH);
        sourcePane.add(sourceListScroll, BorderLayout.CENTER);

        JScrollPane candidateListScroll = titledScrollPane(candidateList, "Ranked target candidates");
        acceptButton.setToolTipText("Accept this candidate and go to the next unmapped class (Enter)");
        acceptButton.getAccessibleContext().setAccessibleName("Accept");
        // Accept is the one main action here, so it looks like a default button. Enter is bound on the two lists
        // rather than the window, so it never fires while the filter field has focus.
        acceptButton.putClientProperty("FlatLaf.style", "background:$Button.default.background;"
            + "foreground:$Button.default.foreground;borderColor:$Button.default.borderColor;"
            + "focusedBorderColor:$Button.default.focusedBorderColor");
        rejectButton.setToolTipText("Reject this candidate (Delete)");
        decisionBar.add(acceptButton);
        decisionBar.add(rejectButton);
        decisionBar.add(clearButton);
        JPanel candidatePane = new JPanel(new BorderLayout());
        candidatePane.setOpaque(false);
        candidatePane.add(candidateListScroll, BorderLayout.CENTER);
        candidatePane.add(decisionBar, BorderLayout.SOUTH);
        JSplitPane lists = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePane, candidatePane);
        lists.setResizeWeight(0.5);
        lists.setDividerSize(5);

        JScrollPane sourceDetailsScroll = titledScrollPane(sourceDetails, "Source details");
        JScrollPane candidateDetailsScroll = titledScrollPane(candidateDetails, "Candidate details");
        // Equal preferred widths, so the long lines of one class do not squeeze the other side.
        sourceDetailsScroll.setPreferredSize(new Dimension(300, 200));
        candidateDetailsScroll.setPreferredSize(new Dimension(300, 200));
        JSplitPane details = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourceDetailsScroll, candidateDetailsScroll);
        details.setResizeWeight(0.5);
        details.setDividerSize(5);

        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lists, details);
        workspace.setResizeWeight(0.45);
        workspace.setDividerSize(5);
        add(workspace, BorderLayout.CENTER);

        differenceLegend.setForeground(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(differenceLegend, BorderLayout.NORTH);
        statusBar.add(statusLabel);
        bottom.add(statusBar, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void configureRenderers() {
        sourceClassList.setCellRenderer(new LabelRenderer<>(ClassNode.class, sourceClass ->
            acceptedMapping(sourceClass)
                .map(mapping -> "✓  " + sourceClass.name + "   → " + mapping.targetClassName() + "   "
                    + mapping.similarityPercent() + "%")
                .orElse("    " + sourceClass.name)));
        candidateList.setCellRenderer(new LabelRenderer<>(ClassMatch.class, candidate ->
            candidate.similarityPercent() + "%  " + candidate.classNode().name + candidateState(candidate)));
    }

    private void reloadSourceClasses() {
        String sourceJar = selectedSourceJar();
        String previousClassName = Optional.ofNullable(sourceClassList.getSelectedValue())
            .map(classNode -> classNode.name)
            .orElse(null);
        sourceClassModel.clear();
        candidateModel.clear();
        allSourceClasses.clear();

        if (!hasJarPair()) {
            updateProgress();
            sourceDetails.setText("");
            candidateDetails.setText("");
            updateDetailsAndControls();
            return;
        }

        List<ClassNode> classes = controller.jars().classes(sourceJar);
        if (classes != null) {
            classes.stream()
                .sorted(Comparator.comparing(classNode -> classNode.name))
                .forEach(allSourceClasses::add);
        }
        fillSourceModel();

        int selectionIndex = findSourceIndex(previousClassName);
        if (selectionIndex < 0 && !sourceClassModel.isEmpty()) {
            selectionIndex = 0;
        }
        if (selectionIndex >= 0) {
            sourceClassList.setSelectedIndex(selectionIndex);
        }
        updateDetailsAndControls();
    }

    private void fillSourceModel() {
        String filter = filterField.getText().strip().toLowerCase(java.util.Locale.ROOT);
        String show = (String) showBox.getSelectedItem();
        sourceClassModel.clear();
        for (ClassNode classNode : allSourceClasses) {
            boolean mapped = acceptedMapping(classNode).isPresent();
            if (SHOW_UNMAPPED.equals(show) && mapped || SHOW_MAPPED.equals(show) && !mapped) {
                continue;
            }
            if (!filter.isEmpty() && !classNode.name.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                continue;
            }
            sourceClassModel.addElement(classNode);
        }
        updateProgress();
    }

    private void applyFilter() {
        ClassNode selected = sourceClassList.getSelectedValue();
        fillSourceModel();
        int index = selected == null ? -1 : sourceClassModel.indexOf(selected);
        if (index < 0 && !sourceClassModel.isEmpty()) {
            index = 0;
        }
        if (index >= 0) {
            sourceClassList.setSelectedIndex(index);
            sourceClassList.ensureIndexIsVisible(index);
        } else {
            refreshCandidates();
        }
    }

    private void updateProgress() {
        if (!hasJarPair()) {
            progressLabel.setText(" ");
            return;
        }
        long mapped = controller.mappings().accepted(selectedSourceJar(), selectedTargetJar()).size();
        java.text.NumberFormat format = java.text.NumberFormat.getIntegerInstance();
        progressLabel.setText(format.format(mapped) + " of " + format.format(allSourceClasses.size())
            + " classes mapped" + (sourceClassModel.size() == allSourceClasses.size() ? ""
            : " · showing " + format.format(sourceClassModel.size())));
    }

    // After accepting, go to the next class not yet mapped, so a run of decisions needs no mouse.
    private void selectNextUnmapped() {
        for (int index = sourceClassList.getSelectedIndex() + 1; index < sourceClassModel.size(); index++) {
            if (acceptedMapping(sourceClassModel.get(index)).isEmpty()) {
                sourceClassList.setSelectedIndex(index);
                sourceClassList.ensureIndexIsVisible(index);
                return;
            }
        }
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
            statusLabel.setText("Accepted " + sourceClass.name + " → " + candidate.classNode().name);
            repaintLists();
            updateProgress();
            if (SHOW_UNMAPPED.equals(showBox.getSelectedItem())) {
                int index = sourceClassList.getSelectedIndex();
                fillSourceModel();
                if (!sourceClassModel.isEmpty()) {
                    sourceClassList.setSelectedIndex(Math.min(index, sourceClassModel.size() - 1));
                }
            } else {
                selectNextUnmapped();
            }
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
        updateProgress();
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
                    updateProgress();
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

        Optional<File> chosen = MappingExportDialog.chooseDestination(this, sourceJar, targetJar);
        if (chosen.isEmpty()) {
            return;
        }
        File destination = chosen.get();
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
        sourceDetails.setText(ClassDetails.describe(sourceClass, null));
        candidateDetails.setText(ClassDetails.describe(
            candidate == null ? null : candidate.classNode(),
            candidate == null ? null : candidate.similarityPercent()
        ));
        highlightDifferences(sourceDetails, sourceClass, candidate == null ? null : candidate.classNode());
        highlightDifferences(candidateDetails, candidate == null ? null : candidate.classNode(), sourceClass);
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
                () -> statusLabel.setText(candidatePrompt(sourceClass, candidate))
            );
        }
    }

    private static String candidatePrompt(ClassNode sourceClass, ClassMatch candidate) {
        if (candidate == null) {
            return "Select a candidate to accept or reject.";
        }
        return "Press Enter to accept " + candidate.classNode().name + " (" + candidate.similarityPercent()
            + "%) as the match for " + sourceClass.name + ", or pick another candidate.";
    }

    private static void highlightDifferences(JTextArea area, ClassNode shown, ClassNode other) {
        area.getHighlighter().removeAllHighlights();
        if (shown == null || other == null) {
            return;
        }
        javax.swing.text.Highlighter.HighlightPainter painter =
            new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Theme.SEARCH_HIGHLIGHT_COLOR);
        for (int line : ClassDetails.unmatchedMembers(area.getText(), shown, other)) {
            try {
                area.getHighlighter().addHighlight(area.getLineStartOffset(line), area.getLineEndOffset(line), painter);
            } catch (javax.swing.text.BadLocationException ignored) {
                // The line came from this text, so it exists.
            }
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

    private void repaintLists() {
        sourceClassList.repaint();
        candidateList.repaint();
    }

    private boolean hasJarPair() {
        return jars.hasPair();
    }

    private String selectedSourceJar() {
        return jars.source();
    }

    private String selectedTargetJar() {
        return jars.target();
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

    private void showError(String title, Throwable throwable) {
        String detail = throwable == null ? "Unknown error" : throwable.getMessage();
        statusLabel.setText(title + ": " + detail);
        JOptionPane.showMessageDialog(this, detail, title, JOptionPane.ERROR_MESSAGE);
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        toolbar.setBackground(Theme.BACKGROUND_COLOR);
        decisionBar.setBackground(Theme.BACKGROUND_COLOR);
        statusBar.setBackground(Theme.BACKGROUND_COLOR);
        differenceLegend.setForeground(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
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
