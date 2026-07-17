package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.service.ReferenceResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ReferenceWorkspacePanel extends JPanel {
    private final Rikr controller;
    private final JLabel targetLabel = new JLabel("Right-click a class, field, or method and choose Show References.");
    private final JTabbedPane resultTabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel("Double-click a result or press Enter to open it.");
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final List<JList<ReferenceResult>> resultLists = new ArrayList<>();

    public ReferenceWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("References and Call Hierarchy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        header.add(title, BorderLayout.NORTH);
        header.add(targetLabel, BorderLayout.SOUTH);
        footer.add(statusLabel, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
        add(resultTabs, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        showEmptyState();

        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    public void showClassReferences(String jarName, ClassNode targetClass) {
        targetLabel.setText("Type: " + targetClass.name + " in " + jarName);
        resetTabs();
        addResultsTab("Type references", controller.findTypeReferences(jarName, targetClass));
        selectFirstResult();
    }

    public void showFieldReferences(String jarName, ClassNode owner, FieldNode field) {
        targetLabel.setText("Field: " + owner.name + "." + field.name + " " + field.desc + " in " + jarName);
        resetTabs();
        addResultsTab("Reads", controller.findFieldReads(jarName, owner, field));
        addResultsTab("Writes", controller.findFieldWrites(jarName, owner, field));
        selectFirstResult();
    }

    public void showMethodReferences(String jarName, ClassNode owner, MethodNode method) {
        targetLabel.setText("Method: " + owner.name + "." + method.name + method.desc + " in " + jarName);
        resetTabs();
        addResultsTab("Callers", controller.findMethodCallers(jarName, owner, method));
        addResultsTab("Callees", controller.findMethodCallees(jarName, owner, method));
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

    private void addResultsTab(String title, List<ReferenceResult> results) {
        DefaultListModel<ReferenceResult> model = new DefaultListModel<>();
        results.forEach(model::addElement);
        JList<ReferenceResult> resultList = new JList<>(model);
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
        for (JList<ReferenceResult> resultList : resultLists) {
            if (resultList.getModel().getSize() > 0) {
                resultList.setSelectedIndex(0);
                return;
            }
        }
    }

    private void openSelectedResult(JList<ReferenceResult> resultList) {
        ReferenceResult result = resultList.getSelectedValue();
        if (result == null) {
            return;
        }
        if (!result.isNavigable()) {
            Toolkit.getDefaultToolkit().beep();
            statusLabel.setText("The referenced class is not loaded in this JAR.");
            return;
        }
        controller.getUserInterface().openReferenceResult(result);
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        header.setBackground(Theme.BACKGROUND_COLOR);
        footer.setBackground(Theme.BACKGROUND_COLOR);
        resultTabs.setBackground(Theme.BACKGROUND_COLOR);
        for (JList<ReferenceResult> resultList : resultLists) {
            resultList.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        }
        revalidate();
        repaint();
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
            if (value instanceof ReferenceResult result) {
                label.setText(result.text());
                label.setToolTipText(result.isNavigable() ? "Open reference" : "Referenced class is not loaded");
                if (!result.isNavigable() && !isSelected) {
                    label.setForeground(Color.GRAY);
                }
            }
            return label;
        }
    }
}
