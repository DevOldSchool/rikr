package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMappingService;
import org.de.rikr.mapping.CandidateRanking;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.model.ClassRef;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.NameTransferAction;
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
import java.util.concurrent.Callable;
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
    // Of the candidates shown: how many share the best similarity, and how many were left out of the list.
    private int tiedCandidates;
    private int hiddenCandidates;
    private final JList<ClassNode> sourceClassList = new JList<>(sourceClassModel);
    private final JList<ClassMatch> candidateList = new JList<>(candidateModel);
    private final ClassComparisonPanel comparison = new ClassComparisonPanel();
    private final JButton acceptButton = new JButton("Accept ↩");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton clearButton = new JButton("Clear source decisions");
    private final JButton autoMatchButton = new JButton("Auto-match");
    private final JButton exportButton = new JButton("Export JSON");
    private static final String CARRY_NAMES_TOOLTIP =
        "Match fields and methods of the mapped classes, and give the target JAR the names given in the source JAR";
    private final JButton carryNamesButton = new JButton("Carry names…");
    private final JLabel statusLabel = new JLabel("Load at least two JARs to begin mapping.");
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel decisionBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final List<ClassNode> allSourceClasses = new ArrayList<>();
    private final JTextField filterField = new JTextField(12);
    private final JComboBox<String> showBox = new JComboBox<>(new String[]{SHOW_ALL, SHOW_UNMAPPED, SHOW_MAPPED});
    private final JLabel progressLabel = new JLabel(" ");
    private final JLabel differenceLegend = new JLabel(
        "A member facing a dash, highlighted, has no member of the same shape in the other class.");

    private int candidateRequestSequence;
    // Background matching in progress, whose button stays off until it finishes.
    private boolean autoMatching;
    private boolean carryingNames;
    // The outcome of the last workspace action, shown instead of the hint until another source class is selected.
    private String notice;
    private ClassNode noticeSource;

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
        carryNamesButton.addActionListener(event -> carryNames());
        updateDetailsAndControls();
    }

    public void refreshJars(Map<String, List<ClassNode>> jarClasses) {
        jars.setJars(jarClasses.keySet().stream().sorted().toList());
        reloadSourceClasses();
    }

    private void buildToolbar() {
        toolbar.add(labelFor("Source JAR", jars.sourceBox()));
        toolbar.add(jars.sourceBox());
        toolbar.add(labelFor("Target JAR", jars.targetBox()));
        toolbar.add(jars.targetBox());
        toolbar.add(labelFor("Minimum %", minimumSimilaritySpinner));
        toolbar.add(minimumSimilaritySpinner);
        autoMatchButton.setToolTipText("Match every class whose best candidate is unique and at least the minimum "
            + "similarity in shape, then more from the fields and methods of the classes matched, also at least the "
            + "minimum. Auto-match's earlier matches below the minimum are dropped; matches you accepted stay.");
        toolbar.add(autoMatchButton);
        toolbar.add(exportButton);
        carryNamesButton.setToolTipText(CARRY_NAMES_TOOLTIP);
        toolbar.add(carryNamesButton);
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
        showBox.getAccessibleContext().setAccessibleName("Show");
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

        comparison.setBorder(BorderFactory.createTitledBorder("Compared by shape"));
        comparison.setPreferredSize(new Dimension(600, 200));

        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lists, comparison);
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
        tiedCandidates = 0;
        hiddenCandidates = 0;
        allSourceClasses.clear();

        if (!hasJarPair()) {
            updateProgress();
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
        tiedCandidates = 0;
        hiddenCandidates = 0;
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
                    List<ClassMatch> found = get();
                    List<ClassMatch> kept = CandidateRanking.forSource(sourceClass, found);
                    hiddenCandidates = found.size() - kept.size();
                    tiedCandidates = CandidateRanking.tiedAtTop(kept);
                    Optional<ClassMatch> accepted = acceptedMatch(sourceClass, targetJar);
                    if (accepted.isPresent()) {
                        kept = CandidateRanking.withAccepted(kept, accepted.get());
                    }
                    kept.forEach(candidateModel::addElement);
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
        // A tie is not a match: picking the first would let Enter accept a coin flip.
        if (!candidateModel.isEmpty() && tiedCandidates < 2) {
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
        statusLabel.setText("Rejected " + sourceClass.name + " → " + candidate.classNode().name);
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
        autoMatching = true;
        autoMatchButton.setEnabled(false);
        statusLabel.setText("Auto-matching unique candidates...");
        // A run with a higher minimum replaces Auto-match's own weaker matches; those people accepted stay.
        int dropped = controller.mappings().removeAutomaticBelow(sourceJar, targetJar, minimumSimilarity);
        Callable<List<ClassMappingService.ProposedMatch>> proposals =
            controller.mappings().autoMatchLater(sourceJar, targetJar, minimumSimilarity);

        // Candidates are found in the background; they are accepted here, on the thread that changes the project.
        new SwingWorker<List<ClassMappingService.ProposedMatch>, Void>() {
            @Override
            protected List<ClassMappingService.ProposedMatch> doInBackground() throws Exception {
                return proposals.call();
            }

            @Override
            protected void done() {
                try {
                    int byShape = controller.mappings().acceptAutoMatches(sourceJar, targetJar, get()).size();
                    repaintLists();
                    updateProgress();
                    refreshCandidates();
                    showNotice("Matched " + byShape + " classes by shape. Matching more from their members...");
                    matchFromMembers(sourceJar, targetJar, minimumSimilarity, dropped, byShape);
                } catch (InterruptedException exception) {
                    finishAutoMatch();
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Auto-match interrupted.");
                } catch (ExecutionException exception) {
                    finishAutoMatch();
                    showError("Auto-match failed", exception.getCause());
                }
            }
        }.execute();
    }

    // Classes whose shape changed, such as when a deobfuscator removed parameters, are found by their members.
    private void matchFromMembers(String sourceJar, String targetJar, int minimumSimilarity, int dropped,
                                  int byShape) {
        controller.runInBackground(
            "Matching classes of " + sourceJar + " and " + targetJar + " from their members",
            controller.mappings().voteClassesLater(sourceJar, targetJar),
            voted -> {
                int fromMembers = controller.mappings().acceptVoted(sourceJar, targetJar, voted, minimumSimilarity)
                    .size();
                finishAutoMatch();
                repaintLists();
                updateProgress();
                refreshCandidates();
                showNotice((dropped == 0 ? "" : "Dropped " + dropped + " earlier automatic "
                    + (dropped == 1 ? "match" : "matches") + " below " + minimumSimilarity + "%. ")
                    + "Auto-matched " + byShape + " classes by shape and " + fromMembers
                    + " more from their members, all at least " + minimumSimilarity + "%.");
            },
            exception -> {
                finishAutoMatch();
                showError("Auto-match failed", exception);
            });
    }

    private void finishAutoMatch() {
        autoMatching = false;
        updateDetailsAndControls();
    }

    private void carryNames() {
        if (!hasJarPair()) {
            return;
        }
        carryingNames = true;
        carryNamesButton.setEnabled(false);
        statusLabel.setText("Matching fields and methods...");
        NameTransferAction.carry(controller, this, selectedSourceJar(), selectedTargetJar(), message -> {
            carryingNames = false;
            showNotice(message);
            repaintLists();
        });
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
        comparison.show(hasJarPair() ? sourceClass : null, candidate == null ? null : candidate.classNode(),
            candidate == null ? null : candidate.similarityPercent());

        boolean hasSource = hasJarPair() && sourceClass != null;
        boolean hasCandidate = hasSource && candidate != null;
        acceptButton.setEnabled(hasCandidate);
        rejectButton.setEnabled(hasCandidate);
        clearButton.setEnabled(hasSource);
        autoMatchButton.setEnabled(hasJarPair() && !autoMatching);
        exportButton.setEnabled(hasJarPair());
        boolean anyAccepted = hasJarPair()
            && !controller.mappings().accepted(selectedSourceJar(), selectedTargetJar()).isEmpty();
        carryNamesButton.setEnabled(hasJarPair() && !carryingNames && anyAccepted);
        // A greyed button says nothing on its own; the tooltip and the status line say what makes it available.
        carryNamesButton.setToolTipText(hasJarPair() && !anyAccepted
            ? "Accept at least one class mapping first, then carry names"
            : CARRY_NAMES_TOOLTIP);

        // Reloading the lists passes through no selection, which must not count as choosing another class.
        if (sourceClass != null && sourceClass != noticeSource || !hasJarPair()) {
            notice = null;
        }
        if (notice != null) {
            statusLabel.setText(notice);
        } else if (!hasJarPair()) {
            statusLabel.setText("Load at least two JARs to begin mapping.");
        } else if (sourceClass == null) {
            statusLabel.setText("Select a source class.");
        } else if (candidateModel.isEmpty() && candidateList.isEnabled()) {
            statusLabel.setText("No candidates meet the minimum similarity.");
        } else {
            Optional<ClassMappingDecision> accepted = acceptedMapping(sourceClass);
            accepted.ifPresentOrElse(
                mapping -> statusLabel.setText(
                    "Accepted: " + mapping.sourceClassName() + " → " + mapping.targetClassName()
                        + " (" + mapping.similarityPercent() + "%)"
                ),
                () -> statusLabel.setText(candidatePrompt(sourceClass, candidate) + hiddenNote(sourceClass))
            );
        }
        if (notice == null && hasJarPair() && !anyAccepted && !carryingNames) {
            statusLabel.setText("<html>" + escape(statusLabel.getText())
                + "<br>Carry names… needs at least one accepted class mapping.</html>");
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showNotice(String message) {
        notice = message;
        noticeSource = sourceClassList.getSelectedValue();
        updateDetailsAndControls();
    }

    private String candidatePrompt(ClassNode sourceClass, ClassMatch candidate) {
        if (candidate == null && tiedCandidates > 1) {
            return "No unique match for " + sourceClass.name + ": " + tiedCandidates + " candidates tie at "
                + candidateModel.get(0).similarityPercent() + "%, so none is picked. Compare their members, then "
                + "choose one.";
        }
        if (candidate == null) {
            return "Select a candidate to accept or reject.";
        }
        return "Press Enter to accept " + candidate.classNode().name + " (" + candidate.similarityPercent()
            + "%) as the match for " + sourceClass.name + ", or pick another candidate.";
    }

    private String hiddenNote(ClassNode sourceClass) {
        if (hiddenCandidates == 0) {
            return "";
        }
        String left = hiddenCandidates == 1 ? " candidate in a package is" : " candidates in packages are";
        return " " + hiddenCandidates + left + " left out, as " + sourceClass.name + " is in no package.";
    }

    // The class accepted as the source class's match, with the similarity it had when accepted.
    private Optional<ClassMatch> acceptedMatch(ClassNode sourceClass, String targetJar) {
        return acceptedMapping(sourceClass).flatMap(decision -> controller.jars()
            .findClass(new ClassRef(targetJar, decision.targetClassName()))
            .map(target -> new ClassMatch(target, decision.similarityPercent())));
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
        comparison.applyTheme();
        revalidate();
        repaint();
    }

    // A label that names its control for screen readers too.
    private static JLabel labelFor(String text, JComponent control) {
        JLabel label = new JLabel(text);
        label.setLabelFor(control);
        control.getAccessibleContext().setAccessibleName(text);
        // A spinner's focus goes to its text field, which a screen reader names on its own.
        if (control instanceof JSpinner spinner && spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().getAccessibleContext().setAccessibleName(text);
            label.setLabelFor(editor.getTextField());
        }
        return label;
    }

    private static JScrollPane titledScrollPane(Component component, String title) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(BorderFactory.createTitledBorder(title));
        return scrollPane;
    }
}
