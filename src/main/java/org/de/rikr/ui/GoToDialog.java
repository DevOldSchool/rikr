package org.de.rikr.ui;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Go to a class, field or method by name, across every loaded JAR (Cmd+K). With nothing typed it lists the classes
 * opened most recently. Type part of a name; exact and prefix matches come first, classes before members.
 */
final class GoToDialog {
    private static final int MAX_RESULTS = 200;

    /**
     * A class, or a field or method of one.
     */
    record Target(String jar, ClassNode owner, FieldNode field, MethodNode method) {
        String name() {
            if (field != null) {
                return field.name;
            }
            if (method != null) {
                return method.name;
            }
            return owner.name.substring(owner.name.lastIndexOf('/') + 1);
        }

        String detail() {
            if (field != null) {
                return ClassNodeUtil.simpleName(Type.getType(field.desc)) + "   in " + owner.name;
            }
            if (method != null) {
                Type type = Type.getMethodType(method.desc);
                List<String> arguments = new ArrayList<>();
                for (Type argument : type.getArgumentTypes()) {
                    arguments.add(ClassNodeUtil.simpleName(argument));
                }
                return "(" + String.join(", ", arguments) + ") " + ClassNodeUtil.simpleName(type.getReturnType()) + "   in "
                    + owner.name;
            }
            return owner.name.contains("/") ? owner.name + "   class" : "class";
        }

        String kind() {
            return field != null ? "field" : method != null ? "method" : "class";
        }
    }

    private GoToDialog() {
    }

    static void open(JFrame frame, Map<String, List<ClassNode>> classesByJar, List<Target> recent,
                     Consumer<Target> go) {
        List<Target> all = new ArrayList<>();
        classesByJar.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            for (ClassNode classNode : entry.getValue()) {
                all.add(new Target(entry.getKey(), classNode, null, null));
                for (FieldNode field : classNode.fields) {
                    all.add(new Target(entry.getKey(), classNode, field, null));
                }
                for (MethodNode method : classNode.methods) {
                    if (!method.name.startsWith("<")) {
                        all.add(new Target(entry.getKey(), classNode, null, method));
                    }
                }
            }
        });

        JDialog dialog = new JDialog(frame, "Go to Class or Member", true);
        JTextField query = new JTextField(40);
        query.putClientProperty("JTextField.placeholderText", "Class, field or method name");
        query.getAccessibleContext().setAccessibleName("Class, field or method name");
        DefaultListModel<Target> model = new DefaultListModel<>();
        JList<Target> results = new JList<>(model);
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.setVisibleRowCount(14);
        boolean manyJars = classesByJar.size() > 1;
        results.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Target target) {
                    String jar = manyJars ? "   · " + target.jar() : "";
                    label.setText("<html><b>" + escape(target.name()) + "</b>&nbsp;&nbsp;<span style='color:"
                        + SecondaryText.color(list, selected) + "'>"
                        + escape(target.detail() + jar) + "</span></html>");
                    label.setIcon(icon(target));
                }
                return label;
            }
        });
        JLabel status = new JLabel(" ");

        Runnable filter = () -> {
            model.clear();
            String text = query.getText().strip().toLowerCase(Locale.ROOT);
            if (text.isEmpty()) {
                recent.forEach(model::addElement);
                status.setText(recent.isEmpty() ? "Type part of a name" : "Recently opened");
            } else {
                List<Target> found = all.stream()
                    .filter(target -> target.name().toLowerCase(Locale.ROOT).contains(text)
                        || target.owner().name.toLowerCase(Locale.ROOT).equals(text))
                    .sorted(Comparator.comparingInt((Target target) -> rank(target, text))
                        // In an obfuscated JAR the program's own code is in no package; libraries keep theirs.
                        .thenComparing(target -> target.owner().name.indexOf('/') >= 0)
                        .thenComparing(Target::name)
                        .thenComparing(target -> target.owner().name))
                    .limit(MAX_RESULTS + 1L)
                    .toList();
                found.stream().limit(MAX_RESULTS).forEach(model::addElement);
                status.setText(found.isEmpty() ? "No class, field or method matches"
                    : found.size() > MAX_RESULTS ? "First " + MAX_RESULTS + " matches; type more to narrow them"
                    : found.size() + (found.size() == 1 ? " match" : " matches"));
            }
            if (!model.isEmpty()) {
                results.setSelectedIndex(0);
            }
        };
        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                filter.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                filter.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                filter.run();
            }
        });
        Runnable choose = () -> {
            Target target = results.getSelectedValue();
            if (target != null) {
                dialog.dispose();
                go.accept(target);
            }
        };
        query.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                int size = model.getSize();
                switch (event.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        results.setSelectedIndex(Math.min(size - 1, results.getSelectedIndex() + 1));
                        results.ensureIndexIsVisible(results.getSelectedIndex());
                        event.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        results.setSelectedIndex(Math.max(0, results.getSelectedIndex() - 1));
                        results.ensureIndexIsVisible(results.getSelectedIndex());
                        event.consume();
                    }
                    case KeyEvent.VK_ENTER -> choose.run();
                    case KeyEvent.VK_ESCAPE -> dialog.dispose();
                    default -> {
                    }
                }
            }
        });
        results.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    choose.run();
                }
            }
        });

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(query, BorderLayout.NORTH);
        content.add(new JScrollPane(results), BorderLayout.CENTER);
        status.setFont(status.getFont().deriveFont(status.getFont().getSize2D() - 1));
        content.add(status, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().registerKeyboardAction(event -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        filter.run();
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 720), dialog.getHeight());
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /**
     * Lower is better: an exact name, then a name that starts with the text, then one that contains it; classes
     * before members at each level.
     */
    static int rank(Target target, String text) {
        String name = target.name().toLowerCase(Locale.ROOT);
        int match = name.equals(text) ? 0 : name.startsWith(text) ? 2 : 4;
        return match + (target.field() == null && target.method() == null ? 0 : 1);
    }

    private static Icon icon(Target target) {
        if (target.field() != null) {
            return ClassNodeImages.getFieldNodeImage(target.field());
        }
        if (target.method() != null) {
            return ClassNodeImages.getMethodNodeImage(target.method());
        }
        return ClassNodeImages.getImage("class");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
