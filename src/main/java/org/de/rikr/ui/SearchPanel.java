package org.de.rikr.ui;

import org.de.rikr.Rikr;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class SearchPanel extends JPanel {
    private final Rikr controller;
    private final JTextField searchField;
    private final JLabel resultCounter;
    private final Color highlightColor;

    private final List<int[]> searchResults;
    private int currentResultIndex;

    public SearchPanel(Rikr controller, ActionListener closeListener) {
        this.controller = controller;

        setLayout(new BorderLayout());

        // Initialize components
        searchField = new JTextField(20);
        JButton searchButton = new JButton("Search");
        resultCounter = new JLabel("0/0");
        JButton upButton = new JButton("Up");
        JButton downButton = new JButton("Down");
        JButton closeButton = new JButton("Close");

        highlightColor = new Color(45, 84, 63);
        searchResults = new ArrayList<>();
        currentResultIndex = -1;

        searchButton.addActionListener(e -> search());
        upButton.addActionListener(e -> navigateResults(-1));
        downButton.addActionListener(e -> navigateResults(1));
        closeButton.addActionListener(closeListener);

        JPanel topPanel = new JPanel();
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(resultCounter);
        topPanel.add(upButton);
        topPanel.add(downButton);
        topPanel.add(closeButton);

        add(topPanel, BorderLayout.NORTH);
    }

    public void search() {
        String searchText = searchField.getText();

        // Remove previous highlights
        removeHighlights(controller.getContentPane());

        searchResults.clear();
        currentResultIndex = -1;

        // Highlight new search results and store positions
        if (!searchText.isEmpty()) {
            highlightText(controller.getContentPane(), searchText);
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
            int pos = 0;

            // Search for pattern and highlight
            while ((pos = text.indexOf(pattern, pos)) >= 0) {
                int endPos = pos + pattern.length();
                hilite.addHighlight(pos, endPos, new DefaultHighlighter.DefaultHighlightPainter(highlightColor));
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
        controller.getContentPane().setCaretPosition(pos[0]);
        controller.getContentPane().moveCaretPosition(pos[1]);
        updateResultCounter();
    }

    private void updateResultCounter() {
        int totalResults = searchResults.size();
        int currentIndex = totalResults > 0 ? currentResultIndex + 1 : 0;
        resultCounter.setText(currentIndex + "/" + totalResults);
    }
}
