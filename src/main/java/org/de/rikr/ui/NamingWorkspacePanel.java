package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.rename.NamingWorklist;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.action.SymbolActions;
import org.de.rikr.ui.model.SymbolTarget;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.List;
import java.util.Map;

/**
 * The Naming workspace: how many of a JAR's classes have names a person gave, and the unnamed classes to name next,
 * most referred to first. Choosing one opens it; Rename names it, and the list moves on.
 */
public class NamingWorkspacePanel extends JPanel {
    private static final int LISTED = 50;

    private final Rikr controller;
    private final JComboBox<String> jarBox = new JComboBox<>();
    private final JLabel progressLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel listHeading = new JLabel("Most referenced, not yet named");
    private final DefaultListModel<NamingWorklist.Entry> model = new DefaultListModel<>();
    private final JList<NamingWorklist.Entry> list = new JList<>(model);
    private final JButton skip = new JButton("Skip");
    private final JButton rename = new JButton("Rename…");
    private boolean updating;

    public NamingWorkspacePanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Naming");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        jarBox.getAccessibleContext().setAccessibleName("JAR");
        jarBox.addActionListener(event -> {
            if (!updating) {
                refresh();
            }
        });
        progressBar.getAccessibleContext().setAccessibleName("Classes named");
        for (JComponent component : List.of(title, jarBox, progressLabel, progressBar, listHeading)) {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        jarBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, jarBox.getPreferredSize().height));
        head.add(title);
        head.add(Box.createVerticalStrut(8));
        head.add(jarBox);
        head.add(Box.createVerticalStrut(8));
        head.add(progressLabel);
        head.add(Box.createVerticalStrut(6));
        head.add(progressBar);
        head.add(Box.createVerticalStrut(14));
        head.add(listHeading);
        add(head, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Classes to name next");
        list.setCellRenderer(new EntryRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updating) {
                openSelected();
            }
            updateButtons();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    renameSelected();
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "rename");
        list.getActionMap().put("rename", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                renameSelected();
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        skip.setToolTipText("Leave this class for now and go to the next");
        skip.addActionListener(event -> skipSelected());
        rename.setToolTipText("Give this class a name (Enter, or Shift+F6)");
        rename.addActionListener(event -> renameSelected());
        rename.putClientProperty("FlatLaf.style", "background:$Button.default.background;"
            + "foreground:$Button.default.foreground");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(skip);
        actions.add(rename);
        add(actions, BorderLayout.SOUTH);
        updateButtons();
    }

    /**
     * Offer the loaded JARs, keeping the chosen one when it is still loaded.
     */
    public void refreshJars(Map<String, List<ClassNode>> jarClasses) {
        Object chosen = jarBox.getSelectedItem();
        updating = true;
        jarBox.removeAllItems();
        jarClasses.keySet().stream().sorted().forEach(jarBox::addItem);
        if (chosen != null && jarClasses.containsKey(chosen.toString())) {
            jarBox.setSelectedItem(chosen);
        }
        jarBox.setVisible(jarBox.getItemCount() > 1);
        updating = false;
        if (isShowing()) {
            refresh();
        }
    }

    /**
     * Count again and list the next classes, keeping the chosen one when it is still unnamed.
     */
    public void refresh() {
        String jar = (String) jarBox.getSelectedItem();
        NamingWorklist.Entry chosen = list.getSelectedValue();
        updating = true;
        model.clear();
        List<ClassNode> classes = jar == null ? null : controller.jars().classes(jar);
        if (classes == null) {
            progressLabel.setText("Load a JAR to start naming its classes.");
            progressBar.setValue(0);
            listHeading.setVisible(false);
            updating = false;
            updateButtons();
            return;
        }
        NamingWorklist.Progress progress = NamingWorklist.of(classes, controller.jars()::originalName,
            classNode -> controller.references().findTypeReferences(jar, classNode).size(), LISTED);
        NumberFormat count = NumberFormat.getIntegerInstance();
        progressLabel.setText(count.format(progress.named()) + " of " + count.format(progress.classes())
            + (progress.classes() == 1 ? " class named" : " classes named"));
        progressBar.setMaximum(Math.max(1, progress.classes()));
        progressBar.setValue(progress.named());
        listHeading.setText(progress.next().isEmpty() ? "Every class has a name." : "Most referenced, not yet named");
        listHeading.setVisible(true);
        progress.next().forEach(model::addElement);
        int keep = chosen == null ? -1 : indexOf(chosen.classNode());
        updating = false;
        list.setSelectedIndex(Math.max(0, keep));
        updateButtons();
    }

    private int indexOf(ClassNode classNode) {
        for (int index = 0; index < model.size(); index++) {
            if (model.get(index).classNode() == classNode) {
                return index;
            }
        }
        return -1;
    }

    private void openSelected() {
        NamingWorklist.Entry entry = list.getSelectedValue();
        if (entry != null) {
            controller.getUserInterface().displayBytecode(entry.classNode());
        }
    }

    private void renameSelected() {
        NamingWorklist.Entry entry = list.getSelectedValue();
        String jar = (String) jarBox.getSelectedItem();
        if (entry == null || jar == null) {
            return;
        }
        controller.getUserInterface().getSymbolActions().run(SymbolActions.Action.RENAME,
            new SymbolTarget(jar, new Declaration.ClassDeclaration(entry.classNode())));
    }

    private void skipSelected() {
        int next = list.getSelectedIndex() + 1;
        if (next < model.size()) {
            list.setSelectedIndex(next);
            list.ensureIndexIsVisible(next);
        }
    }

    private void updateButtons() {
        NamingWorklist.Entry entry = list.getSelectedValue();
        rename.setEnabled(entry != null);
        rename.setText(entry == null ? "Rename…" : "Rename " + entry.classNode().name + "…");
        skip.setEnabled(entry != null && list.getSelectedIndex() + 1 < model.size());
    }

    private static final class EntryRenderer extends JPanel implements ListCellRenderer<NamingWorklist.Entry> {
        private final JLabel name = new JLabel();
        private final JLabel references = new JLabel();

        EntryRenderer() {
            super(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            name.setIcon(ClassNodeImages.getImage("class"));
            add(name, BorderLayout.WEST);
            add(references, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends NamingWorklist.Entry> list,
                                                      NamingWorklist.Entry value, int index, boolean selected,
                                                      boolean focused) {
            name.setText(value.classNode().name);
            references.setText(NumberFormat.getIntegerInstance().format(value.references())
                + (value.references() == 1 ? " reference" : " references"));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            references.setForeground(selected ? list.getSelectionForeground() : Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
            name.setFont(list.getFont());
            references.setFont(list.getFont());
            return this;
        }
    }
}
