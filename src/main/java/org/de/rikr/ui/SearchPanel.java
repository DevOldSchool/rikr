package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.SearchResultItem;
import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Modifier;
import java.util.List;

public class SearchPanel extends JPanel {
    private final Rikr controller;
    private final JTextField searchField;
    private final JList<SearchResultItem> resultsList;
    private final DefaultListModel<SearchResultItem> listModel;
    private final JButton matchCaseButton;
    private final JButton matchWordButton;
    private boolean matchCase;
    private boolean matchWord;
    private final Color defaultBackgroundColor;

    public SearchPanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(null);

        // Create the search bar
        JPanel searchBarPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        matchCaseButton = new JButton(Images.getImage("match-case"));
        matchCaseButton.setToolTipText("Match Case");
        matchCaseButton.setBorderPainted(false);
        matchWordButton = new JButton(Images.getImage("match-word"));
        matchWordButton.setToolTipText("Match Words");
        matchWordButton.setBorderPainted(false);

        // Create a panel for the buttons
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBackground(Theme.BACKGROUND_COLOR);
        buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
        buttonsPanel.add(matchCaseButton);
        buttonsPanel.add(matchWordButton);

        // Add components to the main panel
        searchBarPanel.add(searchField, BorderLayout.CENTER);
        searchBarPanel.add(buttonsPanel, BorderLayout.EAST);

        // Create the list model and the results list
        listModel = new DefaultListModel<>();
        resultsList = new JList<>(listModel);
        resultsList.setCellRenderer(new SearchResultItemRenderer());
        resultsList.setBackground(Theme.BACKGROUND_COLOR);
        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(null);

        // Add components to the panel
        add(searchBarPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        defaultBackgroundColor = matchCaseButton.getBackground();
        matchCase = false;
        matchWord = false;
    }

    public void init() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                performSearch();
            }
        });

        searchField.addActionListener(e -> performSearch());

        matchCaseButton.addActionListener(e -> {
            matchCase = !matchCase;
            toggleButton(matchCaseButton, matchCase);
        });

        matchWordButton.addActionListener(e -> {
            matchWord = !matchWord;
            toggleButton(matchWordButton, matchWord);
        });

        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchResultItem selectedItem = resultsList.getSelectedValue();

                if (selectedItem == null) {
                    return;
                }

                controller.getUserInterface().displayBytecode(selectedItem.getClassNode());

                SwingUtilities.invokeLater(() -> {
                    controller.getUserInterface().getContentPanel().selectTextAfterPattern(selectedItem.getPattern(), selectedItem.getName());
                });
            }
        });
    }

    public void performSearch() {
        listModel.clear();

        String query = searchField.getText();
        if (query.length() < 2) {
            return;
        }

        if (!matchCase) {
            query = query.toLowerCase();
        }

        if (!query.isEmpty()) {
            for (String jarName : controller.getJarClassesMap().keySet()) {
                List<ClassNode> classes = controller.getJarClassesMap().get(jarName);

                for (ClassNode classNode : classes) {
                    if (areEqualStrings(classNode.name, query)) {
                        Icon resultItemIcon;

                        if (Modifier.isInterface(classNode.access)) {
                            resultItemIcon = ClassNodeImages.getImage("interface");
                        } else {
                            resultItemIcon = ClassNodeImages.getImage("class");
                        }

                        listModel.addElement(new SearchResultItem(
                                classNode.name,
                                resultItemIcon,
                                classNode,
                                classNode.name,
                                ClassNodeUtil.getPattern(classNode, null, null)));
                    }

                    // Search interfaces implemented by the class
                    for (String interfaceName : classNode.interfaces) {
                        if (areEqualStrings(interfaceName, query)) {
                            listModel.addElement(new SearchResultItem(
                                    interfaceName,
                                    ClassNodeImages.getImage("interface"),
                                    classNode,
                                    classNode.name,
                                    ClassNodeUtil.getPattern(classNode, null, null)));
                        }
                    }

                    // Search class-level annotations
                    addAnnotationMatchesForClass(classNode, query);

                    // Search fields and their annotations
                    for (FieldNode fieldNode : classNode.fields) {
                        if (areEqualStrings(fieldNode.name, query)) {
                            listModel.addElement(new SearchResultItem(
                                    fieldNode.name,
                                    ClassNodeImages.getFieldNodeImage(fieldNode),
                                    classNode,
                                    fieldNode.name,
                                    ClassNodeUtil.getFieldNodePattern(fieldNode)));
                        }

                        if (areEqualStrings(fieldNode.desc, query)) {
                            listModel.addElement(new SearchResultItem(
                                    fieldNode.desc,
                                    ClassNodeImages.getFieldNodeImage(fieldNode),
                                    classNode,
                                    fieldNode.desc,
                                    ClassNodeUtil.getFieldNodePattern(fieldNode)));
                        }

                        addAnnotationMatchesForField(classNode, fieldNode, query);
                    }

                    // Search methods, descriptors, instructions, and method-level annotations
                    for (MethodNode methodNode : classNode.methods) {
                        if (areEqualStrings(methodNode.name, query)) {
                            listModel.addElement(new SearchResultItem(
                                    methodNode.name,
                                    ClassNodeImages.getMethodNodeImage(methodNode),
                                    classNode,
                                    methodNode.name,
                                    ClassNodeUtil.getMethodNodePattern(methodNode)
                            ));
                        }

                        if (areEqualStrings(methodNode.desc, query)) {
                            listModel.addElement(new SearchResultItem(
                                    methodNode.desc,
                                    ClassNodeImages.getMethodNodeImage(methodNode),
                                    classNode,
                                    methodNode.name,
                                    ClassNodeUtil.getMethodNodePattern(methodNode)
                            ));
                        }

                        addAnnotationMatchesForMethod(classNode, methodNode, query);

                        for (AbstractInsnNode abstractInsnNode : methodNode.instructions) {
                            if (abstractInsnNode instanceof LdcInsnNode ldcInsnNode) {
                                if (ldcInsnNode.cst instanceof String stringCst) {
                                    if (!areEqualStrings(stringCst, query)) {
                                        continue;
                                    }

                                    listModel.addElement(new SearchResultItem(
                                            stringCst,
                                            Images.getImage("instruction"),
                                            classNode,
                                            "\"" + stringCst + "\"",
                                            "LDC \"" + stringCst + "\""));
                                }

                                if (ldcInsnNode.cst instanceof Integer intCst) {
                                    if (!areEqualStrings(intCst.toString(), query)) {
                                        continue;
                                    }

                                    listModel.addElement(new SearchResultItem(
                                            intCst.toString(),
                                            Images.getImage("instruction"),
                                            classNode,
                                            intCst.toString(),
                                            "LDC " + intCst));
                                }

                                if (ldcInsnNode.cst instanceof Long longCst) {
                                    if (!areEqualStrings(longCst.toString(), query)) {
                                        continue;
                                    }

                                    listModel.addElement(new SearchResultItem(
                                            longCst.toString(),
                                            Images.getImage("instruction"),
                                            classNode,
                                            longCst.toString(),
                                            "LDC " + longCst));
                                }
                            }

                            if (abstractInsnNode instanceof TypeInsnNode typeInsnNode) {
                                if (areEqualStrings(typeInsnNode.desc, query)) {
                                    listModel.addElement(new SearchResultItem(
                                            typeInsnNode.desc,
                                            Images.getImage("instruction"),
                                            classNode,
                                            typeInsnNode.desc,
                                            "NEW " + typeInsnNode.desc
                                    ));
                                }
                            }

                            if (abstractInsnNode instanceof FieldInsnNode fieldInsnNode) {
                                if (areEqualStrings(fieldInsnNode.name, query)) {
                                    listModel.addElement(new SearchResultItem(
                                            fieldInsnNode.name,
                                            Images.getImage("instruction"),
                                            classNode,
                                            fieldInsnNode.name,
                                            "GETSTATIC " + fieldInsnNode.owner + "." + fieldInsnNode.name
                                    ));
                                }
                            }

                            if (abstractInsnNode instanceof MethodInsnNode methodInsnNode) {
                                if (areEqualStrings(methodInsnNode.name, query)) {
                                    listModel.addElement(new SearchResultItem(
                                            methodInsnNode.name,
                                            Images.getImage("instruction"),
                                            classNode,
                                            methodInsnNode.name,
                                            "INVOKEINTERFACE " + methodInsnNode.owner + "." + methodInsnNode.name + " " + methodInsnNode.desc
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void toggleButton(JButton button, boolean toggle) {
        if (toggle) {
            button.setBackground(Theme.SEARCH_BUTTON_SELECTED_COLOR);
        } else {
            button.setBackground(defaultBackgroundColor);
        }

        performSearch();
    }

    // ----- Annotation search helpers -----
    private void addAnnotationMatchesForClass(ClassNode classNode, String query) {
        Icon icon = Modifier.isInterface(classNode.access) ? ClassNodeImages.getImage("interface") : ClassNodeImages.getImage("class");
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode an : classNode.visibleAnnotations) {
                addAnnotationResultIfMatch(icon, classNode, null, null, an, query);
            }
        }
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode an : classNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(icon, classNode, null, null, an, query);
            }
        }
    }

    private void addAnnotationMatchesForField(ClassNode owner, FieldNode fieldNode, String query) {
        if (fieldNode.visibleAnnotations != null) {
            for (AnnotationNode an : fieldNode.visibleAnnotations) {
                addAnnotationResultIfMatch(ClassNodeImages.getFieldNodeImage(fieldNode), owner, fieldNode, null, an, query);
            }
        }
        if (fieldNode.invisibleAnnotations != null) {
            for (AnnotationNode an : fieldNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(ClassNodeImages.getFieldNodeImage(fieldNode), owner, fieldNode, null, an, query);
            }
        }
    }

    private void addAnnotationMatchesForMethod(ClassNode owner, MethodNode methodNode, String query) {
        if (methodNode.visibleAnnotations != null) {
            for (AnnotationNode an : methodNode.visibleAnnotations) {
                addAnnotationResultIfMatch(ClassNodeImages.getMethodNodeImage(methodNode), owner, null, methodNode, an, query);
            }
        }
        if (methodNode.invisibleAnnotations != null) {
            for (AnnotationNode an : methodNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(ClassNodeImages.getMethodNodeImage(methodNode), owner, null, methodNode, an, query);
            }
        }
    }

    private void addAnnotationResultIfMatch(Icon icon, ClassNode classNode, FieldNode fieldNode, MethodNode methodNode, AnnotationNode an, String query) {
        String desc = an.desc != null ? an.desc : "";
        String simple = getAnnotationSimpleName(desc);
        String withAt = "@" + simple;
        String fqcn = getAnnotationClassName(desc);

        // Build full signature strings including element key/value pairs
        String fullDescSignature = formatAnnotation(an, true);   // @Lpkg/Ann;(key="val")
        String fullFqcnSignature = fqcn != null ? formatAnnotationWithName(an, fqcn) : null; // @pkg.Ann(key="val")

        // Also prepare value-only strings for matching (e.g., just "om" or value=...)
        boolean valuesMatch = annotationElementsMatch(an, query);

        if (areEqualStrings(desc, query)
                || areEqualStrings(simple, query)
                || areEqualStrings(withAt, query)
                || (fqcn != null && areEqualStrings(fqcn, query))
                || (fullDescSignature != null && areEqualStrings(fullDescSignature, query))
                || (fullFqcnSignature != null && areEqualStrings(fullFqcnSignature, query))
                || valuesMatch) {
            String text = withAt;
            String name;
            String pattern;
            if (methodNode != null) {
                name = methodNode.name;
                pattern = ClassNodeUtil.getMethodNodePattern(methodNode);
            } else if (fieldNode != null) {
                name = fieldNode.name;
                pattern = ClassNodeUtil.getFieldNodePattern(fieldNode);
            } else {
                name = classNode.name;
                pattern = ClassNodeUtil.getPattern(classNode, null, null);
            }

            listModel.addElement(new SearchResultItem(text, icon, classNode, name, pattern));
        }
    }

    private String getAnnotationSimpleName(String desc) {
        if (desc == null) return "";
        // desc example: Ljava/lang/Deprecated; -> Deprecated
        int lastSlash = desc.lastIndexOf('/') + 1;
        int end = desc.endsWith(";") ? desc.length() - 1 : desc.length();
        int start = desc.startsWith("L") ? 1 : 0;
        int simpleStart = Math.max(lastSlash, start);
        if (simpleStart >= end) return desc;
        return desc.substring(simpleStart, end);
    }

    private String getAnnotationClassName(String desc) {
        if (desc == null) return null;
        // Lpkg/Name; -> pkg.Name
        String s = desc;
        if (s.startsWith("L")) s = s.substring(1);
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
        return s.replace('/', '.');
    }

    // Build full annotation string like @Lpkg/Ann;(key="val") or @pkg.Ann(key="val")
    private String formatAnnotation(AnnotationNode an, boolean useDescriptorName) {
        String name = useDescriptorName ? (an.desc != null ? an.desc : "") : getAnnotationClassName(an.desc);
        return formatAnnotationWithName(an, name);
    }

    private String formatAnnotationWithName(AnnotationNode an, String displayName) {
        StringBuilder sb = new StringBuilder();
        sb.append('@');
        sb.append(displayName != null ? displayName : "");
        if (an.values != null && !an.values.isEmpty()) {
            sb.append('(');
            for (int i = 0; i < an.values.size(); i += 2) {
                String key = String.valueOf(an.values.get(i));
                Object val = an.values.get(i + 1);
                sb.append(key).append('=');
                sb.append(formatAnnotationValue(val));
                if (i + 2 < an.values.size()) sb.append(',').append(' ');
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private String formatAnnotationValue(Object val) {
        if (val == null) return "null";
        if (val instanceof String s) {
            return "\"" + s + "\"";
        }
        if (val instanceof Type t) {
            return t.getDescriptor();
        }
        if (val instanceof String[] enumArr && enumArr.length == 2) {
            // {desc, name}
            return enumArr[0] + "." + enumArr[1];
        }
        if (val instanceof AnnotationNode nested) {
            return formatAnnotation(nested, true);
        }
        if (val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatAnnotationValue(list.get(i)));
                if (i + 1 < list.size()) sb.append(',').append(' ');
            }
            sb.append('}');
            return sb.toString();
        }
        return String.valueOf(val);
    }

    private boolean annotationElementsMatch(AnnotationNode an, String query) {
        if (an.values == null || an.values.isEmpty()) return false;
        for (int i = 0; i < an.values.size(); i += 2) {
            String key = String.valueOf(an.values.get(i));
            Object val = an.values.get(i + 1);
            String valStr = formatAnnotationValue(val);
            // Try a few variants
            if (areEqualStrings(key, query)) return true;
            if (areEqualStrings(valStr, query)) return true;
            // Unquoted value for strings
            if (val instanceof String s && areEqualStrings(s, query)) return true;
            // key=value variants
            String pair = key + "=" + valStr;
            if (areEqualStrings(pair, query)) return true;
        }
        return false;
    }

    private boolean areEqualStrings(String first, String second) {
        if (!matchCase) {
            first = first.toLowerCase();
            second = second.toLowerCase();
        }

        if (matchWord) {
            return first.equals(second);
        }

        return first.contains(second);
    }

    public void select() {
        searchField.grabFocus();
    }
}
