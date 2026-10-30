package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.resources.Images;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.SearchResultItem;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SearchPanel extends JPanel {
    private final Rikr controller;
    private final JTextField searchField;
    private final JList<SearchResultItem> resultsList;
    private final JLabel summary = new JLabel(" ");
    private final DefaultListModel<SearchResultItem> listModel;
    private final JButton matchCaseButton;
    private final JButton matchWordButton;
    private boolean matchCase;
    private boolean matchWord;
    private Color defaultBackgroundColor;
    private JPanel buttonsPanel;
    private JPanel searchBarPanel;
    private JScrollPane scrollPane;
    private static final int SEARCH_DEBOUNCE_MS = 250;
    private Timer searchDebounceTimer;

    public SearchPanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());
        setBorder(null);

        // Create the search bar
        searchBarPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        SearchFields.style(searchField, "Search names and instructions");
        matchCaseButton = new JButton(Images.getImage("match-case"));
        matchCaseButton.setToolTipText("Match Case");
        matchCaseButton.getAccessibleContext().setAccessibleName("Match Case");
        matchCaseButton.setBorderPainted(false);
        matchWordButton = new JButton(Images.getImage("match-word"));
        matchWordButton.setToolTipText("Match Words");
        matchWordButton.getAccessibleContext().setAccessibleName("Match Words");
        matchWordButton.setBorderPainted(false);

        // Create a panel for the buttons
        buttonsPanel = new JPanel();
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
        scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(null);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(searchBarPanel, BorderLayout.NORTH);
        top.add(summary, BorderLayout.SOUTH);

        // Add components to the panel
        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        defaultBackgroundColor = matchCaseButton.getBackground();
        matchCase = false;
        matchWord = false;

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        if (searchField != null) {
            SearchFields.applyTheme(searchField);
            SearchFields.toggleIcon(matchCaseButton, "match-case", matchCase);
            SearchFields.toggleIcon(matchWordButton, "match-word", matchWord);
        }

        if (buttonsPanel != null) {
            buttonsPanel.setBackground(Theme.BACKGROUND_COLOR);
        }

        if (resultsList != null) {
            resultsList.setBackground(Theme.BACKGROUND_COLOR);
            resultsList.repaint();
        }

        if (scrollPane != null) {
            Color track = SettingsManager.getScrollBarTrackColor();

            scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.LOWER_LEFT_CORNER, PanelUtil.createCorner());
            scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, PanelUtil.createCorner());

            // Match scrollbars to track color for seamless corners
            JScrollBar vsb = scrollPane.getVerticalScrollBar();
            if (vsb != null) {
                vsb.setBackground(track);
            }
            JScrollBar hsb = scrollPane.getHorizontalScrollBar();
            if (hsb != null) {
                hsb.setBackground(track);
            }
        }

        defaultBackgroundColor = matchCaseButton.getBackground();
        matchCaseButton.setBackground(matchCase ? Theme.SEARCH_BUTTON_SELECTED_COLOR : defaultBackgroundColor);
        matchWordButton.setBackground(matchWord ? Theme.SEARCH_BUTTON_SELECTED_COLOR : defaultBackgroundColor);

        revalidate();
        repaint();
    }

    public void init() {
        searchDebounceTimer = new Timer(SEARCH_DEBOUNCE_MS, e -> performSearch());
        searchDebounceTimer.setRepeats(false);

        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
                    if (searchField.getSelectionStart() != searchField.getSelectionEnd()) {
                        int len = searchField.getText() != null ? searchField.getText().length() : 0;
                        searchField.setCaretPosition(len);
                    }
                }
                triggerSearchDebounced();
            }
        });

        searchField.addActionListener(e -> triggerSearchImmediate());

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

                if (selectedItem == null || !selectedItem.isSelectable() || selectedItem.getPattern() == null) {
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

        SearchQuery query = new SearchQuery(searchField.getText(), matchCase, matchWord);
        List<SearchResult> results = controller.search(query);
        Map<String, List<ClassNode>> classesByJar = controller.jars().classesByJar();
        Map<ClassNode, String> jarOf = new IdentityHashMap<>();
        classesByJar.forEach((jar, classes) -> classes.forEach(classNode -> jarOf.put(classNode, jar)));

        // Results arrive as a class followed by its matching members; keep each class's block under its JAR.
        Map<String, List<SearchResultItem>> byJar = new TreeMap<>();
        Map<String, Integer> matchesByJar = new HashMap<>();
        int matches = 0;
        int classes = 0;
        for (SearchResult result : results) {
            String jar = jarOf.getOrDefault(result.classNode(), "");
            byJar.computeIfAbsent(jar, key -> new ArrayList<>()).add(toResultItem(result));
            if (result.indentLevel() == 0) {
                classes++;
            }
            if (result.selectable()) {
                matches++;
                matchesByJar.merge(jar, 1, Integer::sum);
            }
        }
        boolean manyJars = byJar.size() > 1;
        byJar.forEach((jar, items) -> {
            if (manyJars) {
                int count = matchesByJar.getOrDefault(jar, 0);
                listModel.addElement(new SearchResultItem(jar + " · " + count + (count == 1 ? " match" : " matches"),
                    null, null, null, null, 0, false));
            }
            items.forEach(listModel::addElement);
        });

        if (query.text().isEmpty()) {
            summary.setText(" ");
        } else if (matches == 0) {
            summary.setText("No matches");
        } else {
            summary.setText(matches + (matches == 1 ? " match" : " matches") + " in " + classes
                + (classes == 1 ? " class" : " classes") + (manyJars ? ", " + byJar.size() + " JARs" : ""));
        }
    }

    private SearchResultItem toResultItem(SearchResult result) {
        return new SearchResultItem(
            result.text(),
            getIcon(result),
            result.classNode(),
            result.name(),
            result.pattern(),
            result.indentLevel(),
            result.selectable()
        );
    }

    private Icon getIcon(SearchResult result) {
        return switch (result.icon()) {
            case CLASS -> Modifier.isInterface(result.classNode().access)
                ? ClassNodeImages.getImage("interface")
                : ClassNodeImages.getImage("class");
            case INTERFACE -> ClassNodeImages.getImage("interface");
            case FIELD -> ClassNodeImages.getFieldNodeImage(result.fieldNode());
            case METHOD -> ClassNodeImages.getMethodNodeImage(result.methodNode());
            case INSTRUCTION -> Images.getImage("instruction");
        };
    }

    private void toggleButton(JButton button, boolean toggle) {
        // Screen readers hear the state the colour shows.
        button.getAccessibleContext().setAccessibleDescription(toggle ? "On" : "Off");
        SearchFields.toggleIcon(button, button == matchCaseButton ? "match-case" : "match-word", toggle);
        if (toggle) {
            button.setBackground(Theme.SEARCH_BUTTON_SELECTED_COLOR);
        } else {
            button.setBackground(defaultBackgroundColor);
        }

        // Use debounce to avoid lag when toggling options rapidly
        triggerSearchDebounced();
    }

    private void triggerSearchDebounced() {
        if (searchDebounceTimer == null) {
            // init() not called yet; fall back to immediate search
            performSearch();
            return;
        }
        searchDebounceTimer.restart();
    }

    private void triggerSearchImmediate() {
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        performSearch();
    }

    public void select() {
        int length = searchField.getText() != null ? searchField.getText().length() : 0;
        searchField.setCaretPosition(length);
        searchField.moveCaretPosition(length);
        searchField.requestFocusInWindow();
    }
}
