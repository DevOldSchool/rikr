package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SearchBar extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(SearchBar.class.getName());

    private final Rikr controller;
    private final JTextField searchField;
    private final JButton upButton;
    private final JButton downButton;
    private final JLabel resultCounter;
    private final MatchOptions matchOptions;
    private boolean matchCase;
    private boolean matchWord;

    private final List<int[]> searchResults;
    private int currentResultIndex;
    private final JPanel topPanel;

    public SearchBar(Rikr controller, ActionListener closeListener) {
        this.controller = controller;

        setLayout(new BorderLayout());

        // Initialize components
        searchField = new JTextField(26);
        SearchFields.style(searchField, "Find in this class");
        matchOptions = new MatchOptions(searchField, options -> {
            matchCase = options.matchCase();
            matchWord = options.matchWord();
            search();
        });
        resultCounter = new JLabel(" ");
        upButton = new JButton("Previous");
        upButton.setToolTipText("Previous match (Shift+Cmd+G)");
        downButton = new JButton("Next");
        downButton.setToolTipText("Next match (Cmd+G)");
        JButton closeButton = new JButton("Close");

        closeButton.addActionListener(closeListener);

        searchResults = new ArrayList<>();
        currentResultIndex = -1;

        topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        topPanel.add(searchField);
        topPanel.add(resultCounter);
        topPanel.add(upButton);
        topPanel.add(downButton);
        topPanel.add(closeButton);
        topPanel.setBackground(Theme.BACKGROUND_COLOR);

        add(topPanel, BorderLayout.NORTH);


        matchCase = false;
        matchWord = false;

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    public void init() {
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateResults(1);
                } else {
                    search();
                }
            }
        });


        upButton.addActionListener(e -> navigateResults(-1));
        downButton.addActionListener(e -> navigateResults(1));
    }

    public void focus() {
        int length = searchField.getText() != null ? searchField.getText().length() : 0;
        searchField.setCaretPosition(length);
        searchField.moveCaretPosition(length);
        searchField.requestFocusInWindow();
    }

    public void search() {
        String query = searchField.getText();
        if (!matchCase) {
            query = query.toLowerCase();
        }


        // Remove previous highlights
        removeHighlights(controller.getUserInterface().getContentPane());

        searchResults.clear();
        currentResultIndex = -1;

        // Highlight new search results and store positions
        if (!query.isEmpty()) {
            highlightText(controller.getUserInterface().getContentPane(), query);
        }

        updateResultCounter();
    }

    private void removeHighlights(JTextComponent textComp) {
        Highlighter hilite = textComp.getHighlighter();
        Highlighter.Highlight[] hilites = hilite.getHighlights();

        for (Highlighter.Highlight hilite1 : hilites) {
            if (hilite1.getPainter() instanceof DefaultHighlighter.DefaultHighlightPainter) {
                hilite.removeHighlight(hilite1);
            }
        }
    }

    private void highlightText(JTextPane contentPane, String pattern) {
        try {
            Highlighter hilite = contentPane.getHighlighter();
            Document doc = contentPane.getDocument();
            String text = doc.getText(0, doc.getLength());

            // Adjust text and pattern case sensitivity
            if (!matchCase) {
                text = text.toLowerCase();
                pattern = pattern.toLowerCase();
            }

            int pos = 0;

            // Search for pattern and highlight
            while ((pos = text.indexOf(pattern, pos)) >= 0) {
                int endPos = pos + pattern.length();

                // Check if whole word match is required
                if (matchWord) {
                    boolean isWordBoundaryBefore = (pos == 0) || !Character.isLetterOrDigit(text.charAt(pos - 1));
                    boolean isWordBoundaryAfter = (endPos == text.length()) || !Character.isLetterOrDigit(text.charAt(endPos));
                    if (!isWordBoundaryBefore || !isWordBoundaryAfter) {
                        pos = endPos;
                        continue;
                    }
                }

                hilite.addHighlight(pos, endPos, new DefaultHighlighter.DefaultHighlightPainter(Theme.SEARCH_HIGHLIGHT_COLOR));
                searchResults.add(new int[]{pos, endPos});
                pos = endPos;
            }
        } catch (BadLocationException e) {
            LOGGER.log(Level.WARNING, "Failed to highlight search results.", e);
        }
    }

    /**
     * Move to the next match (1) or the previous one (-1).
     */
    public void navigate(int direction) {
        navigateResults(direction);
    }

    private void navigateResults(int direction) {
        if (searchResults.isEmpty()) {
            return;
        }

        currentResultIndex += direction;

        if (currentResultIndex < 0) {
            currentResultIndex = searchResults.size() - 1;
        } else if (currentResultIndex >= searchResults.size()) {
            currentResultIndex = 0;
        }

        int[] pos = searchResults.get(currentResultIndex);
        controller.getUserInterface().getContentPane().setCaretPosition(pos[0]);
        controller.getUserInterface().getContentPane().moveCaretPosition(pos[1]);
        updateResultCounter();
    }

    private void updateResultCounter() {
        int totalResults = searchResults.size();
        if (searchField.getText().isEmpty()) {
            resultCounter.setText(" ");
        } else if (totalResults == 0) {
            resultCounter.setText("No matches");
        } else if (currentResultIndex < 0) {
            resultCounter.setText(totalResults + (totalResults == 1 ? " match" : " matches"));
        } else {
            resultCounter.setText((currentResultIndex + 1) + " of " + totalResults);
        }
    }

    private void applyTheme() {
        topPanel.setBackground(Theme.BACKGROUND_COLOR);
        SearchFields.applyTheme(searchField);
        matchOptions.applyTheme();

        revalidate();
        repaint();
    }
}
