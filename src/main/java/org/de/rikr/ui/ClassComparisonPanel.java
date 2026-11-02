package org.de.rikr.ui;

import org.de.rikr.mapping.MemberAlignment;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * A source class and a mapping candidate side by side: what kind of class each is, then their fields and methods
 * lined up by shape (see {@link MemberAlignment}). A member with no partner of its shape faces a dash and is
 * highlighted, so a tie between candidates can be settled by eye.
 */
final class ClassComparisonPanel extends JScrollPane {
    private static final String NONE = "—";

    private final ComparisonModel model = new ComparisonModel();
    private final JTable table = new JTable(model);

    ClassComparisonPanel() {
        setViewportView(table);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(12, 0));
        table.setRowSelectionAllowed(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        table.getAccessibleContext().setAccessibleName("Source class and candidate compared");
        applyTheme();
    }

    /**
     * @param target     the candidate, or null to show the source class alone
     * @param similarity the candidate's similarity, shown in its column's heading
     */
    void show(ClassNode source, ClassNode target, Integer similarity) {
        model.show(source, target, similarity);
        table.getTableHeader().repaint();
        getVerticalScrollBar().setValue(0);
    }

    void applyTheme() {
        table.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        getViewport().setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        // A size smaller than the code's, from the editor font, so it grows with it when Rikr is scaled up.
        Font editor = SettingsManager.getEditorFont();
        Font font = editor.deriveFont(Math.max(10f, editor.getSize2D() - 1));
        table.setFont(font);
        table.setRowHeight(table.getFontMetrics(font).getHeight() + 6);
        repaint();
    }

    private enum Kind {
        FACT,
        HEADING,
        MEMBER
    }

    private record Line(Kind kind, String source, String target) {
        boolean unmatched() {
            return kind == Kind.MEMBER && (source == null || target == null);
        }
    }

    private static final class ComparisonModel extends AbstractTableModel {
        private final List<Line> lines = new ArrayList<>();
        private String sourceHeading = "Source";
        private String targetHeading = "Candidate";
        private boolean hasTarget;

        void show(ClassNode source, ClassNode target, Integer similarity) {
            lines.clear();
            hasTarget = target != null;
            sourceHeading = source == null ? "Source" : "Source: " + source.name;
            targetHeading = target == null ? "Candidate"
                : "Candidate: " + target.name + (similarity == null ? "" : " · " + similarity + "% similar");
            if (source != null) {
                lines.add(new Line(Kind.FACT, kind(source), target == null ? null : kind(target)));
                lines.add(new Line(Kind.FACT, "extends " + superName(source),
                    target == null ? null : "extends " + superName(target)));
                lines.add(new Line(Kind.FACT, interfaces(source), target == null ? null : interfaces(target)));
                List<MemberAlignment.Row> rows = MemberAlignment.align(source, target);
                for (MemberAlignment.Section section : MemberAlignment.Section.values()) {
                    List<MemberAlignment.Row> inSection = rows.stream().filter(row -> row.section() == section).toList();
                    String title = section == MemberAlignment.Section.FIELDS ? "Fields" : "Methods";
                    long sourceCount = inSection.stream().filter(row -> row.source() != null).count();
                    long targetCount = inSection.stream().filter(row -> row.target() != null).count();
                    lines.add(new Line(Kind.HEADING, title + " (" + sourceCount + ")",
                        target == null ? null : title + " (" + targetCount + ")"));
                    inSection.forEach(row -> lines.add(new Line(Kind.MEMBER, row.source(), row.target())));
                }
            }
            fireTableStructureChanged();
        }

        Line line(int row) {
            return lines.get(row);
        }

        boolean hasTarget() {
            return hasTarget;
        }

        @Override
        public int getRowCount() {
            return lines.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? sourceHeading : targetHeading;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Line line = lines.get(row);
            return column == 0 ? line.source() : line.target();
        }

        private static String kind(ClassNode classNode) {
            if (Modifier.isInterface(classNode.access)) {
                return "interface";
            }
            return Modifier.isAbstract(classNode.access) ? "abstract class" : "class";
        }

        private static String superName(ClassNode classNode) {
            return classNode.superName == null ? "nothing" : classNode.superName.replace('/', '.');
        }

        private static String interfaces(ClassNode classNode) {
            return classNode.interfaces.isEmpty() ? "implements nothing"
                : "implements " + String.join(", ", classNode.interfaces).replace('/', '.');
        }
    }

    private final class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable owner, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            super.getTableCellRendererComponent(owner, value, false, false, row, column);
            Line line = model.line(row);
            boolean empty = value == null;
            // A member missing on one side reads as a dash, so the difference is not told by colour alone.
            setText(empty ? (column == 1 && !model.hasTarget() ? "" : NONE) : value.toString());
            setFont(line.kind() == Kind.HEADING ? owner.getFont().deriveFont(Font.BOLD) : owner.getFont());
            setForeground(empty || line.kind() == Kind.FACT ? Theme.CONTENT_HIGHLIGHT_TEXT_COLOR
                : Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR);
            setBackground(line.unmatched() && !empty && model.hasTarget() ? Theme.SEARCH_HIGHLIGHT_COLOR
                : Theme.CONTENT_BACKGROUND_COLOR);
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            return this;
        }
    }
}
