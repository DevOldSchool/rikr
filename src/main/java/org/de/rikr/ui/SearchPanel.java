package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.ui.model.SearchResultItem;
import org.de.rikr.utilities.ClassNodeUtil;
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
                    }

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
