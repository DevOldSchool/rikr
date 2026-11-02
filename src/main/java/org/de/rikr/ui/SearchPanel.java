package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.resources.Images;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.search.SearchResultIcon;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.SearchResultItem;
import org.objectweb.asm.tree.ClassNode;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
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
    private final MatchOptions matchOptions;
    private final Map<Kind, JToggleButton> kindChips = new EnumMap<>(Kind.class);
    private Kind kind = Kind.ALL;
    private List<SearchResult> lastResults = List.of();
    private String lastQuery = "";
    private boolean lastMatchCase;
    private boolean matchCase;
    private boolean matchWord;
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
        SearchFields.style(searchField, "Search names, strings and member references");
        matchOptions = new MatchOptions(searchField, options -> {
            matchCase = options.matchCase();
            matchWord = options.matchWord();
            // Use debounce to avoid lag when toggling options rapidly
            triggerSearchDebounced();
        });

        // Create a panel for the buttons

        // Add components to the main panel
        searchBarPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        searchBarPanel.add(searchField, BorderLayout.CENTER);

        // Create the list model and the results list
        listModel = new DefaultListModel<>();
        resultsList = new JList<>(listModel);
        resultsList.setCellRenderer(new SearchResultItemRenderer(() -> lastQuery, () -> lastMatchCase));
        resultsList.setBackground(Theme.BACKGROUND_COLOR);
        scrollPane = new JScrollPane(resultsList);
        scrollPane.setBorder(null);

        summary.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        // Long counts push the last chip onto a second row; a plain FlowLayout would report one row and clip it.
        JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        WrapLayout.followWidth(chips);
        chips.setOpaque(false);
        ButtonGroup chipGroup = new ButtonGroup();
        for (Kind each : Kind.values()) {
            JToggleButton chip = new JToggleButton(each.label);
            chip.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
            chip.setToolTipText("Show " + each.label.toLowerCase(java.util.Locale.ROOT) + " only");
            chip.setSelected(each == kind);
            chip.addActionListener(e -> {
                kind = each;
                showResults();
            });
            chipGroup.add(chip);
            chips.add(chip);
            kindChips.put(each, chip);
        }
        kindChips.get(Kind.ALL).setToolTipText("Show every match");

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(searchBarPanel, BorderLayout.NORTH);
        top.add(summary, BorderLayout.CENTER);
        top.add(chips, BorderLayout.SOUTH);

        // Add components to the panel
        add(top, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);


        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        if (searchField != null) {
            SearchFields.applyTheme(searchField);
            matchOptions.applyTheme();
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

        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SearchResultItem selectedItem = resultsList.getSelectedValue();

                if (selectedItem == null || !selectedItem.isSelectable() || selectedItem.getPattern() == null) {
                    return;
                }

                controller.getUserInterface().displayBytecode(selectedItem.getClassNode());

                SwingUtilities.invokeLater(() -> controller.getUserInterface().getContentPanel()
                    .selectTextAfterPattern(selectedItem.getPattern(), selectedItem.getName()));
            }
        });
    }

    public void performSearch() {
        SearchQuery query = new SearchQuery(searchField.getText(), matchCase, matchWord);
        lastResults = controller.search(query);
        lastQuery = query.text();
        lastMatchCase = matchCase;
        showResults();
    }

    // Lists the last results of the chosen kind, each under its class and, when there is more than one, its JAR.
    private void showResults() {
        listModel.clear();

        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (SearchResult result : lastResults) {
            if (result.selectable()) {
                counts.merge(Kind.ALL, 1, Integer::sum);
                counts.merge(Kind.of(result), 1, Integer::sum);
            }
        }
        kindChips.forEach((each, chip) -> chip.setText(each.label + " " + String.format("%,d",
            counts.getOrDefault(each, 0))));

        List<SearchResult> shown = filter(lastResults, kind);
        Map<String, List<ClassNode>> classesByJar = controller.jars().classesByJar();
        Map<ClassNode, String> jarOf = new IdentityHashMap<>();
        classesByJar.forEach((jar, classes) -> classes.forEach(classNode -> jarOf.put(classNode, jar)));

        // Results arrive as a class followed by its matching members; keep each class's block under its JAR.
        Map<String, List<SearchResultItem>> byJar = new TreeMap<>();
        Map<String, Integer> matchesByJar = new HashMap<>();
        int matches = 0;
        int classes = 0;
        for (SearchResult result : shown) {
            String jar = jarOf.getOrDefault(result.classNode(), "");
            byJar.computeIfAbsent(jar, key -> new ArrayList<>()).add(toResultItem(result));
            if (result.indentLevel() == 0) {
                classes++;
            }
            if (result.selectable() && (kind == Kind.ALL || Kind.of(result) == kind)) {
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

        if (lastQuery.isEmpty()) {
            summary.setText(" ");
        } else if (matches == 0) {
            summary.setText(lastResults.isEmpty() ? "No matches" : "No matches of this kind");
        } else {
            summary.setText(String.format("%,d", matches) + (matches == 1 ? " match" : " matches") + " in "
                + String.format("%,d", classes) + (classes == 1 ? " class" : " classes")
                + (manyJars ? ", " + byJar.size() + " JARs" : ""));
        }
    }

    /**
     * The results of one kind, each still under its class, and each instruction under the method that holds it.
     */
    static List<SearchResult> filter(List<SearchResult> results, Kind kind) {
        if (kind == Kind.ALL) {
            return results;
        }
        List<SearchResult> shown = new ArrayList<>();
        SearchResult classRow = null;
        SearchResult methodRow = null;
        for (SearchResult result : results) {
            if (result.indentLevel() == 0) {
                classRow = result;
                methodRow = null;
                if (kind == Kind.CLASSES) {
                    shown.add(result);
                    classRow = null;
                }
                continue;
            }
            if (result.icon() == SearchResultIcon.METHOD) {
                methodRow = result;
            }
            if (Kind.of(result) != kind) {
                continue;
            }
            if (classRow != null) {
                shown.add(classRow);
                classRow = null;
            }
            if (kind == Kind.INSTRUCTIONS && methodRow != null) {
                shown.add(methodRow);
                methodRow = null;
            }
            shown.add(result);
        }
        return shown;
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
            case INSTRUCTION -> Images.getInstructionImage();
        };
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

    /**
     * What a search result is, for the chips that narrow the list.
     */
    enum Kind {
        ALL("All"),
        CLASSES("Classes"),
        MEMBERS("Members"),
        INSTRUCTIONS("Instructions");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        static Kind of(SearchResult result) {
            return switch (result.icon()) {
                case CLASS, INTERFACE -> CLASSES;
                case FIELD, METHOD -> MEMBERS;
                case INSTRUCTION -> INSTRUCTIONS;
            };
        }
    }
}
