package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class SearchBar extends JPanel {
    private final Rikr controller;
    private final JTextField searchField;
    private final JButton upButton;
    private final JButton downButton;
    private final JLabel resultCounter;
    private final JButton matchCaseButton;
    private final JButton matchWordButton;
    private boolean matchCase;
    private boolean matchWord;

    private final List<int[]> searchResults;
    private int currentResultIndex;
    private final Color defaultBackgroundColor;

    public SearchBar(Rikr controller, ActionListener closeListener) {
        this.controller = controller;

        setLayout(new BorderLayout());

        // Initialize components
        searchField = new JTextField(20);
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        matchCaseButton = new JButton(Images.getImage("match-case"));
        matchCaseButton.setToolTipText("Match Case");
        matchCaseButton.setBorderPainted(false);
        matchWordButton = new JButton(Images.getImage("match-word"));
        matchWordButton.setToolTipText("Match Words");
        matchWordButton.setBorderPainted(false);
        resultCounter = new JLabel("0/0");
        upButton = new JButton("Up");
        upButton.setBorderPainted(false);
        downButton = new JButton("Down");
        downButton.setBorderPainted(false);
        JButton closeButton = new JButton("Close");
        closeButton.setBorderPainted(false);

        closeButton.addActionListener(closeListener);

        searchResults = new ArrayList<>();
        currentResultIndex = -1;

        JPanel topPanel = new JPanel();
        topPanel.add(searchField);
        topPanel.add(matchCaseButton);
        topPanel.add(matchWordButton);
        topPanel.add(resultCounter);
        topPanel.add(upButton);
        topPanel.add(downButton);
        topPanel.add(closeButton);
        topPanel.setBackground(Theme.BACKGROUND_COLOR);

        add(topPanel, BorderLayout.NORTH);

        defaultBackgroundColor = matchCaseButton.getBackground();

        matchCase = false;
        matchWord = false;
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

        matchCaseButton.addActionListener(e -> {
            matchCase = !matchCase;
            toggleButton(matchCaseButton, matchCase);
        });

        matchWordButton.addActionListener(e -> {
            matchWord = !matchWord;
            toggleButton(matchWordButton, matchWord);
        });

        upButton.addActionListener(e -> navigateResults(-1));
        downButton.addActionListener(e -> navigateResults(1));
    }

    public void focus() {
        searchField.requestFocus();
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
            e.printStackTrace();
        }
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
        int currentIndex = totalResults > 0 ? currentResultIndex + 1 : 0;
        resultCounter.setText(currentIndex + "/" + totalResults);
    }

    private void toggleButton(JButton button, boolean toggle) {
        if (toggle) {
            button.setBackground(Theme.SEARCH_BUTTON_SELECTED_COLOR);
        } else {
            button.setBackground(defaultBackgroundColor);
        }

        search();
    }
}
