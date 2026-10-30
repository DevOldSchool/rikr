package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.mapping.SimilarClassReport;
import org.de.rikr.reference.ReferenceKind;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReferenceWorkspacePanel extends JPanel {
    private final Rikr controller;
    private final JLabel targetLabel = new JLabel("Right-click a class, field, or method and choose Show References.");
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("Double-click a result or press Enter to open it.");
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final List<JList<Object>> resultLists = new ArrayList<>();
    private final JCheckBox follow = new JCheckBox("Follow the open class");
    private ClassNode classTarget;
    // True while this panel opens one of its own results, which must not replace the results being browsed.
    private boolean opening;

    /**
     * A row naming the class the results below it are in. Opening it opens the class.
     */
    private record ClassHeader(String className, ClassNode classNode, int count) {
    }

    /**
     * One member of a class with every way it uses the target, so a method that names the target in its signature
     * and in its code is one row.
     */
    record Row(ReferenceResult result, List<ReferenceKind> kinds) {
        String kindsText() {
            if (kinds.equals(List.of(ReferenceKind.METHOD_TYPE, ReferenceKind.INSTRUCTION_TYPE))) {
                return "in signature and code";
            }
            return String.join(" and ", kinds.stream().map(ReferenceWorkspacePanel::kindText).toList());
        }
    }

    /**
     * Group results into rows, one per member of each class, keeping the order results arrive in.
     */
    static List<Row> rows(List<ReferenceResult> results) {
        Map<String, List<ReferenceResult>> byMember = new LinkedHashMap<>();
        for (ReferenceResult result : results) {
            byMember.computeIfAbsent(ownerName(result) + "\u0000" + memberText(result), key -> new ArrayList<>())
                .add(result);
        }
        return byMember.values().stream()
            .map(same -> new Row(same.getFirst(),
                same.stream().map(ReferenceResult::kind).distinct().sorted().toList()))
            .toList();
    }

    public ReferenceWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("References and Call Hierarchy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        header.add(title, BorderLayout.NORTH);
        header.add(targetLabel, BorderLayout.SOUTH);
        follow.setToolTipText("Show the references of each class you open from the tree, search or code");
        follow.setOpaque(false);
        header.add(follow, BorderLayout.EAST);
        footer.add(statusLabel, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
        add(resultTabs, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        showEmptyState();

        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    /**
     * A class was opened elsewhere; when following, show its references.
     */
    public void classOpened(String jarName, ClassNode classNode) {
        if (follow.isSelected() && !opening && classNode != classTarget) {
            showClassReferences(jarName, classNode);
        }
    }

    public void showClassReferences(String jarName, ClassNode targetClass) {
        classTarget = targetClass;
        targetLabel.setText("Type: " + targetClass.name + " in " + jarName);
        resetTabs();
        addResultsTab("Type references", controller.references().findTypeReferences(jarName, targetClass));
        selectFirstResult();
    }

    public void showFieldReferences(String jarName, ClassNode owner, FieldNode field) {
        classTarget = null;
        targetLabel.setText("Field: " + owner.name + "." + field.name + " " + field.desc + " in " + jarName);
        resetTabs();
        addResultsTab("Reads", controller.references().findFieldReads(jarName, owner, field));
        addResultsTab("Writes", controller.references().findFieldWrites(jarName, owner, field));
        selectFirstResult();
    }

    public void showMethodReferences(String jarName, ClassNode owner, MethodNode method) {
        classTarget = null;
        targetLabel.setText("Method: " + owner.name + "." + method.name + method.desc + " in " + jarName);
        resetTabs();
        addResultsTab("Callers", controller.references().findMethodCallers(jarName, owner, method));
        addResultsTab("Callees", controller.references().findMethodCallees(jarName, owner, method));
        selectFirstResult();
    }

    /**
     * List classes found by a search, such as the classes that extend one, so each can be opened.
     */
    public void showClasses(String description, String jarName, List<ClassNode> classes, ReferenceKind kind,
                            String relation) {
        targetLabel.setText(description + " in " + jarName);
        resetTabs();
        addResultsTab("Classes", classes.stream()
            .map(classNode -> new ReferenceResult(kind, classNode.name + " " + relation, classNode,
                ClassNodeUtil.getPattern(classNode, null, null), classNode.name))
            .toList());
        selectFirstResult();
    }

    /**
     * List the classes in other JARs that look like {@code source}, best first, with the methods that behave the
     * same in both.
     */
    public void showSimilarClasses(String jarName, ClassNode source, SimilarClassReport report) {
        targetLabel.setText("Classes like " + source.name + " in " + jarName + ", in other JARs");
        resetTabs();
        List<ReferenceResult> results = new ArrayList<>();
        for (SimilarClassReport.Match match : report.matches()) {
            ClassNode target = match.match().classNode();
            String methods = match.equivalentMethods().isEmpty() ? "no methods confirmed equivalent"
                : match.equivalentMethods().size() + " equivalent methods: " + match.equivalentMethods().stream()
                    .limit(4)
                    .map(pair -> pair.source().name + " → " + pair.target().name)
                    .reduce((left, right) -> left + ", " + right).orElse("");
            results.add(new ReferenceResult(ReferenceKind.SIMILAR, match.jar() + "." + match.match().similarityPercent()
                + "% similar, " + methods, target, ClassNodeUtil.getPattern(target, null, null), target.name));
        }
        addResultsTab("Similar classes", results);
        if (report.skippedComparisons() > 0) {
            statusLabel.setText(report.skippedComparisons() + " method comparisons could not be simulated.");
        }
        selectFirstResult();
    }

    private void showEmptyState() {
        resetTabs();
        addResultsTab("Results", List.of());
    }

    private void resetTabs() {
        resultTabs.removeAll();
        resultLists.clear();
        statusLabel.setText("Double-click a result or press Enter to open it.");
    }

    /**
     * Add a tab of results, grouped under a header row per class.
     */
    private void addResultsTab(String title, List<ReferenceResult> results) {
        DefaultListModel<Object> model = new DefaultListModel<>();
        Map<String, List<Row>> byClass = new LinkedHashMap<>();
        for (Row row : rows(results)) {
            byClass.computeIfAbsent(ownerName(row.result()), name -> new ArrayList<>()).add(row);
        }
        byClass.forEach((className, members) -> {
            model.addElement(new ClassHeader(className, members.getFirst().result().navigationClass(), members.size()));
            members.forEach(model::addElement);
        });
        JList<Object> resultList = new JList<>(model);
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new ReferenceResultRenderer());
        resultList.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    openSelectedResult(resultList);
                }
            }
        });

        String openAction = "openReferenceResult";
        resultList.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            openAction
        );
        resultList.getActionMap().put(openAction, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openSelectedResult(resultList);
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.setBorder(null);
        resultTabs.addTab(title + " (" + results.size() + ")", scrollPane);
        resultLists.add(resultList);
    }

    private void selectFirstResult() {
        for (JList<Object> resultList : resultLists) {
            for (int index = 0; index < resultList.getModel().getSize(); index++) {
                if (resultList.getModel().getElementAt(index) instanceof Row) {
                    resultList.setSelectedIndex(index);
                    return;
                }
            }
        }
    }

    private void openSelectedResult(JList<Object> resultList) {
        Object selected = resultList.getSelectedValue();
        if (selected instanceof ClassHeader header) {
            if (header.classNode() != null) {
                opening = true;
                try {
                    controller.getUserInterface().openReferenceResult(new ReferenceResult(ReferenceKind.EXTENDS,
                        header.className(), header.classNode(),
                        ClassNodeUtil.getPattern(header.classNode(), null, null), header.classNode().name));
                } finally {
                    opening = false;
                }
            }
            return;
        }
        if (!(selected instanceof Row row)) {
            return;
        }
        ReferenceResult result = row.result();
        if (!result.isNavigable()) {
            Toolkit.getDefaultToolkit().beep();
            statusLabel.setText("The referenced class is not loaded in this JAR.");
            return;
        }
        opening = true;
        try {
            controller.getUserInterface().openReferenceResult(result);
        } finally {
            opening = false;
        }
    }

    /**
     * The class a result is in: the class to open, or for a result outside the JAR the owner in its text.
     */
    private static String ownerName(ReferenceResult result) {
        if (result.kind() == ReferenceKind.SIMILAR) {
            return result.text().substring(0, result.text().indexOf('.')) + "  ·  " + result.navigationClass().name;
        }
        if (result.navigationClass() != null && result.kind() != ReferenceKind.CALLEE) {
            return result.navigationClass().name;
        }
        int dot = result.text().indexOf('.');
        return dot < 0 ? result.text() : result.text().substring(0, dot);
    }

    /**
     * The member a result is in, readably: {@code a(Lwab;B)V -> uses dc} becomes {@code a(wab, byte) void}.
     */
    static String memberText(ReferenceResult result) {
        String text = result.text();
        int arrow = text.indexOf(" -> ");
        if (arrow >= 0) {
            text = text.substring(0, arrow);
        }
        if (result.kind() == ReferenceKind.SIMILAR) {
            return text.substring(text.indexOf('.') + 1);
        }
        int dot = text.indexOf('.');
        if (dot < 0 || result.kind() == ReferenceKind.EXTENDS || result.kind() == ReferenceKind.IMPLEMENTS) {
            return text;
        }
        String member = text.substring(dot + 1);
        int open = member.indexOf('(');
        int space = member.indexOf(' ');
        if (open < 0 && space > 0) {
            // A field with its descriptor, such as "b Lwab;".
            try {
                return member.substring(0, space) + " " + simpleType(Type.getType(member.substring(space + 1)));
            } catch (RuntimeException exception) {
                return member;
            }
        }
        if (open < 0) {
            return member;
        }
        try {
            Type type = Type.getMethodType(member.substring(open));
            List<String> arguments = new ArrayList<>();
            for (Type argument : type.getArgumentTypes()) {
                arguments.add(simpleType(argument));
            }
            return member.substring(0, open) + "(" + String.join(", ", arguments) + ") "
                + simpleType(type.getReturnType());
        } catch (RuntimeException exception) {
            return member;
        }
    }

    private static String simpleType(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static String kindText(ReferenceKind kind) {
        return switch (kind) {
            case CALLER -> "calls it";
            case CALLEE -> "called";
            case FIELD_READ -> "reads";
            case FIELD_WRITE -> "writes";
            case EXTENDS -> "extends";
            case IMPLEMENTS -> "implements";
            case FIELD_TYPE -> "field type";
            case METHOD_TYPE -> "in a signature";
            case INSTRUCTION_TYPE -> "in code";
            case SIMILAR -> "similar";
        };
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        header.setBackground(Theme.BACKGROUND_COLOR);
        footer.setBackground(Theme.BACKGROUND_COLOR);
        resultTabs.setBackground(Theme.BACKGROUND_COLOR);
        for (JList<Object> resultList : resultLists) {
            resultList.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        }
        revalidate();
        repaint();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class ReferenceResultRenderer extends DefaultListCellRenderer {
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
            if (value instanceof ClassHeader header) {
                label.setText("<html><b>" + escape(header.className()) + "</b>&nbsp;&nbsp;" + header.count()
                    + "</html>");
                label.setToolTipText(header.classNode() == null ? null : "Open " + header.className());
                label.setBorder(BorderFactory.createEmptyBorder(index == 0 ? 2 : 8, 6, 2, 6));
            } else if (value instanceof Row row) {
                ReferenceResult result = row.result();
                label.setText("<html>" + escape(memberText(result)) + "&nbsp;&nbsp;&nbsp;<span style='color:"
                    + SecondaryText.color(list, isSelected) + "'>" + row.kindsText() + "</span></html>");
                label.setToolTipText(result.isNavigable() ? result.text() : result.text() + " (class not loaded)");
                label.setBorder(BorderFactory.createEmptyBorder(1, 24, 1, 6));
                if (!result.isNavigable() && !isSelected) {
                    label.setForeground(Color.GRAY);
                }
            }
            return label;
        }
    }
}
