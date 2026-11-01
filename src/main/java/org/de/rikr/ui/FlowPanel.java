package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.flow.CallGraph;
import org.de.rikr.flow.ControlFlowBuilder;
import org.de.rikr.flow.ControlFlowGraph;
import org.de.rikr.flow.EntryPoint;
import org.de.rikr.flow.MethodRef;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.graph.DrawnGraph;
import org.de.rikr.ui.graph.GraphLegend;
import org.de.rikr.ui.graph.GraphView;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Follows code from where a JAR starts. Pick an entry point to see its call graph; expand methods with their badge,
 * double-click a method for its control flow graph, and right-click a block to follow the calls it makes. Back
 * retraces the path.
 */
public class FlowPanel extends JPanel {
    private static final int MAX_CALL_GRAPH_NODES = 400;
    private static final int CALLS_PER_ROW = 6;
    private static final Color TRUE_COLOR = new Color(98, 180, 98);
    private static final Color FALSE_COLOR = new Color(214, 96, 96);
    private static final Color SWITCH_COLOR = new Color(88, 150, 220);
    private static final Color EXCEPTION_COLOR = new Color(220, 150, 60);
    private static final Color LAMBDA_COLOR = new Color(198, 120, 221);

    /**
     * Shows a block of a method in the bytecode view.
     */
    @FunctionalInterface
    public interface BytecodeOpener {
        void open(ClassNode owner, MethodNode method, String label);
    }

    /** One step of the path through the code; Back returns to the previous one. */
    private sealed interface View permits CallView, FlowView {
        String jar();
    }

    /**
     * The calls from a method. Methods are kept as nodes, not names, so the view survives renames.
     */
    private record CallView(String jar, ClassNode owner, MethodNode method, Set<MethodNode> expanded) implements View {
    }

    private record FlowView(String jar, ClassNode owner, MethodNode method) implements View {
    }

    private final Rikr controller;
    private final BytecodeOpener openInBytecode;
    private final GraphView view;
    private final JButton back = new JButton("◀ Back");
    private final JComboBox<String> jarBox = new JComboBox<>();
    private final JComboBox<EntryChoice> entryBox = new JComboBox<>();
    private final JCheckBox libraryCalls = new JCheckBox("Library calls");
    private final JCheckBox exceptionEdges = new JCheckBox("Exception edges", true);
    private final JLabel zoomLabel = new JLabel("100%");
    private final JLabel title = new JLabel("Pick an entry point, or right-click a method in the project tree.");
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final GraphLegend legend = new GraphLegend();
    private final JLabel keys = new JLabel(GraphView.keyHint());
    private Runnable viewListener = () -> {
    };
    private FlowView pending;
    private final JTextField find = new JTextField(14);
    // Before a graph is chosen, the canvas lists the JAR's entry points to start from.
    private static final String GRAPH = "graph";
    private static final String ENTRIES = "entries";
    private final CardLayout canvasCards = new CardLayout();
    private final JPanel canvas = new JPanel(canvasCards);
    private final DefaultListModel<EntryChoice> entries = new DefaultListModel<>();
    private final JList<EntryChoice> entryList = new JList<>(entries);
    private final JLabel entriesHeading = new JLabel();
    private final Deque<View> history = new ArrayDeque<>();
    private final Map<String, CallGraph> callGraphs = new HashMap<>();
    private View current;
    private boolean updatingChoices;

    // What the drawn nodes stand for: methods in a call graph, blocks in a control flow graph.
    private List<MethodRef> drawnMethods = List.of();
    private ControlFlowGraph drawnFlow;

    private record EntryChoice(EntryPoint entry) {
        @Override
        public String toString() {
            if (entry == null) {
                return "Entry point…";
            }
            return kindName(entry.kind()) + ": " + describe(entry.method());
        }
    }

    public FlowPanel(Rikr controller, BytecodeOpener openInBytecode) {
        super(new BorderLayout());
        this.controller = controller;
        this.openInBytecode = openInBytecode;
        this.view = new GraphView(new GraphView.Listener() {
            @Override
            public void doubleClicked(int node) {
                openNode(node);
            }

            @Override
            public void badgeClicked(int node) {
                toggleExpanded(node);
            }

            @Override
            public JPopupMenu menu(int node) {
                return menuFor(node);
            }

            @Override
            public void zoomChanged(double scale) {
                zoomLabel.setText(Math.round(scale * 100) + "%");
            }
        });

        JButton zoomOut = new JButton("−");
        JButton zoomIn = new JButton("+");
        JButton fit = new JButton("Fit");
        zoomOut.setToolTipText("Zoom out");
        zoomOut.getAccessibleContext().setAccessibleName("Zoom out");
        zoomIn.setToolTipText("Zoom in");
        zoomIn.getAccessibleContext().setAccessibleName("Zoom in");
        fit.setToolTipText("Fit the whole graph in view");
        zoomOut.addActionListener(event -> view.zoomOut());
        zoomIn.addActionListener(event -> view.zoomIn());
        fit.addActionListener(event -> view.fit());
        back.addActionListener(event -> back());
        back.setToolTipText("Back to the previous graph");
        SearchFields.style(find, "Find in graph");
        find.setToolTipText("Find the next block or method containing this text (Enter)");
        find.addActionListener(event -> {
            String text = find.getText();
            if (!text.isBlank() && !view.findNext(text)) {
                Toolkit.getDefaultToolkit().beep();
                zoomLabel.setText("No match");
            }
        });
        back.setEnabled(false);
        jarBox.setToolTipText("The JAR to find entry points in");
        jarBox.getAccessibleContext().setAccessibleName("JAR");
        jarBox.addActionListener(event -> jarChosen());
        entryBox.setToolTipText("Where the JAR's code starts: main methods, applets, threads and event listeners");
        entryBox.getAccessibleContext().setAccessibleName("Entry point");
        entryBox.addActionListener(event -> entryChosen());
        libraryCalls.setToolTipText("Show calls to methods outside the JAR, such as the JDK");
        libraryCalls.setOpaque(false);
        libraryCalls.addActionListener(event -> render(true));
        // Obfuscated code often wraps nearly every block in a handler, which buries the real branches.
        exceptionEdges.setToolTipText("Show exception handlers and the edges into them");
        exceptionEdges.setOpaque(false);
        exceptionEdges.addActionListener(event -> render(true));

        toolbar.add(back);
        toolbar.add(jarBox);
        toolbar.add(entryBox);
        toolbar.add(zoomOut);
        toolbar.add(zoomIn);
        toolbar.add(fit);
        toolbar.add(zoomLabel);
        toolbar.add(find);
        toolbar.add(libraryCalls);
        toolbar.add(exceptionEdges);
        JPanel header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        title.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        JPanel caption = new JPanel(new BorderLayout());
        caption.setOpaque(false);
        caption.add(title, BorderLayout.CENTER);
        caption.add(legend, BorderLayout.EAST);
        // The graph's keys are said while it has the focus, where someone using the keyboard is looking.
        keys.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        keys.setVisible(false);
        view.onFocusChange(keys::setVisible);
        caption.add(keys, BorderLayout.SOUTH);
        header.add(caption, BorderLayout.SOUTH);
        header.setOpaque(false);
        add(header, BorderLayout.NORTH);
        // The overview floats over the graph's bottom-right corner.
        JPanel stack = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }
        };
        stack.setLayout(new OverlayLayout(stack));
        JComponent overview = view.overview();
        overview.setAlignmentX(1f);
        overview.setAlignmentY(1f);
        view.setAlignmentX(1f);
        view.setAlignmentY(1f);
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 20));
        corner.add(overview);
        corner.setAlignmentX(1f);
        corner.setAlignmentY(1f);
        corner.setMaximumSize(new Dimension(220, 220));
        stack.add(corner);
        stack.add(view);
        canvas.add(stack, GRAPH);
        canvas.add(entriesPanel(), ENTRIES);
        add(canvas, BorderLayout.CENTER);
        updateToggles();
        showCanvas();
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    private JComponent entriesPanel() {
        entryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entryList.getAccessibleContext().setAccessibleName("Entry points");
        entryList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    openChosenEntry();
                }
            }
        });
        String open = "openEntryPoint";
        entryList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), open);
        entryList.getActionMap().put(open, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openChosenEntry();
            }
        });
        entriesHeading.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(entriesHeading, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(entryList);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void openChosenEntry() {
        EntryChoice choice = entryList.getSelectedValue();
        if (choice != null) {
            entryBox.setSelectedItem(choice);
        }
    }

    private void showCanvas() {
        canvasCards.show(canvas, current == null ? ENTRIES : GRAPH);
    }

    /**
     * Offer the loaded JARs, keeping the chosen one when it is still loaded.
     */
    public void refreshJars(List<String> jars) {
        String chosen = (String) jarBox.getSelectedItem();
        updatingChoices = true;
        jarBox.removeAllItems();
        jars.stream().sorted().forEach(jarBox::addItem);
        if (chosen != null && jars.contains(chosen)) {
            jarBox.setSelectedItem(chosen);
        }
        updatingChoices = false;
        jarChosen();
    }

    /**
     * Choose the JAR and list its entry points, without opening one.
     */
    public void showEntryPoints(String jar) {
        jarBox.setSelectedItem(jar);
        if (current == null) {
            entryList.requestFocusInWindow();
        } else {
            entryBox.requestFocusInWindow();
        }
    }

    public void showCallGraph(String jar, ClassNode owner, MethodNode method) {
        go(new CallView(jar, owner, method, identitySet()));
    }

    public void showControlFlow(String jar, ClassNode owner, MethodNode method) {
        go(new FlowView(jar, owner, method));
    }

    /**
     * Draw the current graph again, for example after a rename changed names.
     */
    public void refresh() {
        callGraphs.clear();
        render(true);
    }

    public void clear() {
        history.clear();
        current = null;
        callGraphs.clear();
        view.clear();
        back.setEnabled(false);
        title.setText("Pick an entry point, or right-click a method in the project tree.");
        legend.show(List.of());
        pending = null;
        updateToggles();
        showCanvas();
        viewListener.run();
    }

    /**
     * What the panel shows, in words for the status bar: the kind of graph and its method, or null when empty.
     */
    public String describeView() {
        return switch (current) {
            case CallView state -> "Calls from " + describe(state.owner(), state.method());
            case FlowView state -> "Control flow of " + describe(state.owner(), state.method());
            case null -> null;
        };
    }

    /**
     * A method as people read it: {@code ar.no(nm, int)}, not {@code ar.no(Lnm;I)V}.
     */
    public static String describe(ClassNode owner, MethodNode method) {
        return MemberText.describe(owner, method);
    }

    private static String describe(MethodRef method) {
        return method.owner().replace('/', '.') + "." + MemberText.signature(method.name(), method.descriptor());
    }

    /**
     * Be told whenever the graph shown changes, by navigation here or from elsewhere.
     */
    public void setViewListener(Runnable listener) {
        viewListener = listener;
    }

    /**
     * A method was chosen elsewhere, in the tree or Go to. A control-flow graph follows it: at once while this
     * panel is on screen, or when it is next shown, so a graph is not built for every click in another workspace.
     * A call graph is left alone: it is something the person built by expanding calls.
     */
    public void methodOpened(String jar, ClassNode owner, MethodNode method) {
        if (!(current instanceof FlowView shown)) {
            return;
        }
        if (shown.owner() == owner && shown.method() == method) {
            return;
        }
        if (isShowing()) {
            pending = null;
            showControlFlow(jar, owner, method);
        } else {
            pending = new FlowView(jar, owner, method);
        }
    }

    /**
     * Show the method chosen while this panel was hidden, if any.
     */
    public void showPending() {
        if (pending != null) {
            FlowView next = pending;
            pending = null;
            go(next);
        }
    }

    private void jarChosen() {
        if (updatingChoices) {
            return;
        }
        String jar = (String) jarBox.getSelectedItem();
        updatingChoices = true;
        entryBox.removeAllItems();
        entryBox.addItem(new EntryChoice(null));
        entries.clear();
        if (jar != null) {
            controller.jars().entryPoints(jar).forEach(entry -> {
                EntryChoice choice = new EntryChoice(entry);
                entryBox.addItem(choice);
                entries.addElement(choice);
            });
        }
        entryBox.setSelectedIndex(0);
        updatingChoices = false;
        if (jar == null) {
            entriesHeading.setText("Load a JAR to see where its code starts.");
        } else if (entries.isEmpty()) {
            entriesHeading.setText("No entry points found in " + jar + ". Right-click a method in the project tree "
                + "to see its calls.");
        } else {
            entriesHeading.setText("Where " + jar + "’s code starts. Double-click one, or press Enter, to see "
                + "its calls.");
            entryList.setSelectedIndex(0);
        }
    }

    private void entryChosen() {
        EntryChoice choice = (EntryChoice) entryBox.getSelectedItem();
        String jar = (String) jarBox.getSelectedItem();
        if (updatingChoices || choice == null || choice.entry() == null || jar == null) {
            return;
        }
        CallGraph calls = callGraph(jar);
        MethodRef method = choice.entry().method();
        Optional<ClassNode> owner = calls.owner(method);
        Optional<MethodNode> node = calls.method(method);
        if (owner.isPresent() && node.isPresent()) {
            showCallGraph(jar, owner.get(), node.get());
            title.setToolTipText(choice.entry().reason());
        }
    }

    private void go(View next) {
        if (current != null) {
            history.push(current);
        }
        current = next;
        back.setEnabled(!history.isEmpty());
        updateToggles();
        showCanvas();
        render(false);
        viewListener.run();
    }

    private void back() {
        if (history.isEmpty()) {
            return;
        }
        current = history.pop();
        back.setEnabled(!history.isEmpty());
        updateToggles();
        showCanvas();
        render(false);
        viewListener.run();
    }

    private void updateToggles() {
        libraryCalls.setVisible(current instanceof CallView);
        exceptionEdges.setVisible(current instanceof FlowView);
    }

    private CallGraph callGraph(String jar) {
        return callGraphs.computeIfAbsent(jar, name -> {
            List<ClassNode> classes = controller.jars().classes(name);
            return new CallGraph(classes == null ? List.of() : List.copyOf(classes));
        });
    }

    private void render(boolean keepView) {
        if (current == null) {
            return;
        }
        if (controller.jars().classes(current.jar()) == null) {
            clear();
            return;
        }
        switch (current) {
            case CallView calls -> renderCalls(calls, keepView);
            case FlowView flow -> renderFlow(flow, keepView);
        }
    }

    private void renderCalls(CallView state, boolean keepView) {
        CallGraph calls = callGraph(state.jar());
        MethodRef root = ref(state.owner(), state.method());
        Map<MethodRef, Integer> index = new LinkedHashMap<>();
        List<DrawnGraph.Edge> edges = new ArrayList<>();
        Deque<MethodRef> pending = new ArrayDeque<>(List.of(root));
        index.put(root, 0);
        Set<MethodRef> expanded = new HashSet<>(List.of(root));
        state.expanded().forEach(method -> findRef(state, method).ifPresent(expanded::add));
        boolean truncated = false;
        while (!pending.isEmpty()) {
            MethodRef method = pending.removeFirst();
            if (!expanded.contains(method)) {
                continue;
            }
            for (CallGraph.Call call : visibleCalls(calls, method)) {
                Integer target = index.get(call.target());
                if (target == null) {
                    if (index.size() >= MAX_CALL_GRAPH_NODES) {
                        truncated = true;
                        continue;
                    }
                    target = index.size();
                    index.put(call.target(), target);
                    pending.add(call.target());
                }
                edges.add(new DrawnGraph.Edge(index.get(method), target, callColor(call.kind()),
                    call.kind() == CallGraph.CallKind.OVERRIDE || call.kind() == CallGraph.CallKind.LAMBDA,
                    call.kind() == CallGraph.CallKind.OVERRIDE ? "override"
                        : call.kind() == CallGraph.CallKind.LAMBDA ? "lambda" : null));
            }
        }
        List<DrawnGraph.Node> nodes = new ArrayList<>();
        drawnMethods = new ArrayList<>(index.keySet());
        for (MethodRef method : drawnMethods) {
            boolean inJar = calls.method(method).isPresent();
            int callCount = inJar ? visibleCalls(calls, method).size() : 0;
            // The method the graph starts from always shows its calls, so it has no badge to toggle them.
            String badge = !inJar || callCount == 0 || method.equals(root) ? null
                : expanded.contains(method) ? "−" : "+" + callCount;
            nodes.add(new DrawnGraph.Node(
                List.of(method.owner() + "." + method.name(), readableDescriptor(method.descriptor())),
                inJar ? Theme.SYNTAX_HIGHLIGHT_KEYWORD_COLOR : Theme.SYNTAX_HIGHLIGHT_COMMENT_COLOR,
                !inJar,
                method.equals(root),
                badge,
                method + (inJar ? "\n\nClick the badge to show or hide its calls.\nDouble-click for its control flow."
                    + "\nRight-click for more." : "\n\nOutside the JAR.")));
        }
        drawnFlow = null;
        view.show(new DrawnGraph(nodes, edges), keepView, CALLS_PER_ROW);
        legend.show(List.of(
            new GraphLegend.Item(Theme.CONTENT_TEXT_COLOR, false, "call"),
            new GraphLegend.Item(SWITCH_COLOR, true, "override"),
            new GraphLegend.Item(LAMBDA_COLOR, true, "lambda")));
        title.setText("Calls from " + describe(root) + ": " + plural(nodes.size(), "method")
            + (truncated ? ", stopped at " + MAX_CALL_GRAPH_NODES : ""));
    }

    private List<CallGraph.Call> visibleCalls(CallGraph calls, MethodRef method) {
        return calls.callees(method).stream()
            .filter(call -> libraryCalls.isSelected() || call.inJar())
            .toList();
    }

    private void renderFlow(FlowView state, boolean keepView) {
        ControlFlowGraph graph = ControlFlowBuilder.build(state.owner().name, state.method());
        if (!exceptionEdges.isSelected()) {
            graph = graph.withoutExceptionEdges();
        }
        List<DrawnGraph.Node> nodes = new ArrayList<>();
        for (ControlFlowGraph.Block block : graph.blocks()) {
            List<String> lines = new ArrayList<>();
            StringBuilder header = new StringBuilder(blockName(block));
            if (!block.lineNumbers().isEmpty()) {
                header.append("   line ").append(block.lineNumbers().getFirst());
                if (block.lineNumbers().size() > 1) {
                    header.append('–').append(block.lineNumbers().getLast());
                }
            }
            if (!block.reachable()) {
                header.append("   unreachable");
            }
            lines.add(header.toString());
            int shown = Math.min(block.lines().size(), 30);
            lines.addAll(block.lines().subList(0, shown));
            if (block.lines().size() > shown) {
                lines.add("… " + (block.lines().size() - shown) + " more instructions");
            }
            String calls = block.calls().isEmpty() ? "" : "\n\nRight-click to follow its " + block.calls().size()
                + (block.calls().size() == 1 ? " call." : " calls.");
            String tooltip = String.join("\n", block.lines()) + "\n\nDouble-click to show it in the bytecode view."
                + calls;
            nodes.add(new DrawnGraph.Node(lines, Theme.SYNTAX_HIGHLIGHT_KEYWORD_COLOR, !block.reachable(),
                block.id() == 0, null, tooltip));
        }
        List<DrawnGraph.Edge> edges = new ArrayList<>();
        for (ControlFlowGraph.Edge edge : graph.edges()) {
            Color color = switch (edge.kind()) {
                case TRUE -> TRUE_COLOR;
                case FALSE -> FALSE_COLOR;
                case SWITCH -> SWITCH_COLOR;
                case EXCEPTION -> EXCEPTION_COLOR;
                case JUMP, FALLTHROUGH -> Theme.CONTENT_TEXT_COLOR;
            };
            String label = edge.label() == null ? null : edge.label().substring(edge.label().lastIndexOf('/') + 1);
            String outcome = switch (edge.kind()) {
                case TRUE -> "T";
                case FALSE -> "F";
                default -> null;
            };
            edges.add(new DrawnGraph.Edge(edge.from(), edge.to(), color,
                edge.kind() == ControlFlowGraph.EdgeKind.EXCEPTION, label, outcome));
        }
        drawnFlow = graph;
        drawnMethods = List.of();
        view.show(new DrawnGraph(nodes, edges), keepView);
        long unreachable = graph.blocks().stream().filter(block -> !block.reachable()).count();
        legend.show(List.of(
            new GraphLegend.Item(TRUE_COLOR, false, "T  branch taken"),
            new GraphLegend.Item(FALSE_COLOR, false, "F  not taken"),
            new GraphLegend.Item(SWITCH_COLOR, false, "switch case"),
            new GraphLegend.Item(EXCEPTION_COLOR, true, "exception handler")));
        title.setText("Control flow of " + describe(state.owner(), state.method()) + ": "
            + plural(graph.blocks().size(), "block") + ", " + plural(graph.edges().size(), "edge")
            + (unreachable == 0 ? "" : ", " + unreachable + " unreachable"));
    }

    private void openNode(int node) {
        switch (current) {
            case CallView state -> methodNodes(state.jar(), drawnMethods.get(node))
                .ifPresent(method -> showControlFlow(state.jar(), method.owner(), method.method()));
            case FlowView state -> openInBytecode.open(state.owner(), state.method(),
                drawnFlow.blocks().get(node).label());
            case null -> {
            }
        }
    }

    private void toggleExpanded(int node) {
        if (!(current instanceof CallView state)) {
            return;
        }
        methodNodes(state.jar(), drawnMethods.get(node)).ifPresent(method -> {
            if (method.method() == state.method()) {
                return;
            }
            if (!state.expanded().remove(method.method())) {
                state.expanded().add(method.method());
            }
            render(true);
            view.select(node);
        });
    }

    private JPopupMenu menuFor(int node) {
        JPopupMenu menu = new JPopupMenu();
        switch (current) {
            case CallView state -> {
                Optional<MethodNodes> method = methodNodes(state.jar(), drawnMethods.get(node));
                if (method.isEmpty()) {
                    return null;
                }
                menu.add(item("Control flow", () -> showControlFlow(state.jar(), method.get().owner(),
                    method.get().method())));
                menu.add(item("Call graph from here", () -> showCallGraph(state.jar(), method.get().owner(),
                    method.get().method())));
                menu.add(item("Show in bytecode", () -> openInBytecode.open(method.get().owner(), method.get().method(),
                    null)));
                if (method.get().method() != state.method()) {
                    boolean shown = state.expanded().contains(method.get().method());
                    menu.add(item(shown ? "Hide its calls" : "Show its calls", () -> toggleExpanded(node)));
                }
            }
            case FlowView state -> {
                ControlFlowGraph.Block block = drawnFlow.blocks().get(node);
                menu.add(item("Show in bytecode", () -> openInBytecode.open(state.owner(), state.method(),
                    block.label())));
                menu.add(item("Call graph from this method", () -> showCallGraph(state.jar(), state.owner(),
                    state.method())));
                CallGraph calls = callGraph(state.jar());
                List<CallGraph.Call> targets = block.calls().stream()
                    .flatMap(site -> calls.targets(site).stream())
                    .distinct()
                    .toList();
                if (!targets.isEmpty()) {
                    menu.addSeparator();
                }
                for (CallGraph.Call call : targets) {
                    Optional<MethodNodes> target = methodNodes(state.jar(), call.target());
                    JMenuItem item = item("Control flow of " + call.target()
                        + (call.kind() == CallGraph.CallKind.OVERRIDE ? "  (override)" : ""),
                        () -> target.ifPresent(found -> showControlFlow(state.jar(), found.owner(), found.method())));
                    item.setEnabled(target.isPresent());
                    if (target.isEmpty()) {
                        item.setToolTipText("Outside the JAR");
                    }
                    menu.add(item);
                }
            }
            case null -> {
                return null;
            }
        }
        return menu;
    }

    private record MethodNodes(ClassNode owner, MethodNode method) {
    }

    private Optional<MethodNodes> methodNodes(String jar, MethodRef ref) {
        CallGraph calls = callGraph(jar);
        Optional<ClassNode> owner = calls.owner(ref);
        Optional<MethodNode> method = calls.method(ref);
        return owner.isPresent() && method.isPresent()
            ? Optional.of(new MethodNodes(owner.get(), method.get()))
            : Optional.empty();
    }

    private Optional<MethodRef> findRef(CallView state, MethodNode method) {
        String owner = ownerOf(state, method);
        return owner == null ? Optional.empty() : Optional.of(new MethodRef(owner, method.name, method.desc));
    }

    /**
     * The class that declares an expanded method, found by identity so it works after renames.
     */
    private String ownerOf(CallView state, MethodNode method) {
        List<ClassNode> classes = controller.jars().classes(state.jar());
        if (classes == null) {
            return null;
        }
        for (ClassNode classNode : classes) {
            for (MethodNode candidate : classNode.methods) {
                if (candidate == method) {
                    return classNode.name;
                }
            }
        }
        return null;
    }

    private static JMenuItem item(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static Set<MethodNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static MethodRef ref(ClassNode owner, MethodNode method) {
        return new MethodRef(owner.name, method.name, method.desc);
    }

    private static String blockName(ControlFlowGraph.Block block) {
        return block.id() == 0 ? "entry" : block.label() == null ? "block " + block.id() : block.label();
    }

    /**
     * A descriptor as Java types: {@code (I[Ljava/lang/String;)V} becomes {@code (int, String[]) void}.
     */
    private static String readableDescriptor(String descriptor) {
        Type type = Type.getMethodType(descriptor);
        List<String> arguments = new ArrayList<>();
        for (Type argument : type.getArgumentTypes()) {
            arguments.add(ClassNodeUtil.simpleName(argument));
        }
        return "(" + String.join(", ", arguments) + ") " + ClassNodeUtil.simpleName(type.getReturnType());
    }

    private static String kindName(EntryPoint.Kind kind) {
        return switch (kind) {
            case MAIN_CLASS -> "Main-Class";
            case MAIN -> "main";
            case APPLET -> "Applet";
            case THREAD -> "Thread";
            case LISTENER -> "Listener";
        };
    }

    private static Color callColor(CallGraph.CallKind kind) {
        return switch (kind) {
            case OVERRIDE -> SWITCH_COLOR;
            case LAMBDA -> LAMBDA_COLOR;
            default -> Theme.CONTENT_TEXT_COLOR;
        };
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        toolbar.setBackground(Theme.BACKGROUND_COLOR);
        keys.setForeground(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
        repaint();
    }
}
