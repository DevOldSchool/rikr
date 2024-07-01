package org.de.rikr.ui.highlighter;

import org.de.rikr.ui.Fonts;

import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {
    private static final Color DEFAULT_COLOR = new Color(188, 190, 196);
    private static final Color KEYWORD_COLOR = new Color(207, 142, 109);
    private static final Color COMMENT_COLOR = new Color(122, 126, 133);
    private static final Color NUMBER_COLOR = new Color(42, 172, 184);
    private static final Color STRING_COLOR = new Color(106, 171, 115);

    // Styles for different types of text
    private static final String DEFAULT_STYLE = "Default";
    private static final String KEYWORD_STYLE = "Keyword";
    private static final String COMMENT_STYLE = "Comment";
    private static final String NUMBER_STYLE = "Number";
    private static final String STRING_STYLE = "String";

    private static final float LINE_HEIGHT = 1.2f;

    private static Font defaultFont;
    private ArrayList<int[]> commentRegions;

    public SyntaxHighlighter() {
        defaultFont = Fonts.getDefaultFont();
    }

    public void highlight(StyledDocument doc, String content) {
        // Clear existing content and styles
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        // Define styles
        Style defaultStyle = doc.addStyle(DEFAULT_STYLE, null);
        StyleConstants.setForeground(defaultStyle, DEFAULT_COLOR);
        StyleConstants.setFontFamily(defaultStyle, defaultFont.getFamily());
        StyleConstants.setFontSize(defaultStyle, defaultFont.getSize());
        StyleConstants.setLineSpacing(defaultStyle, LINE_HEIGHT);
        StyleConstants.setSpaceAbove(defaultStyle, 4);
        StyleConstants.setSpaceBelow(defaultStyle, 4);

        Style keywordStyle = doc.addStyle(KEYWORD_STYLE, defaultStyle);
        StyleConstants.setForeground(keywordStyle, KEYWORD_COLOR);
        StyleConstants.setBold(keywordStyle, true);

        Style commentStyle = doc.addStyle(COMMENT_STYLE, defaultStyle);
        StyleConstants.setForeground(commentStyle, COMMENT_COLOR);
        StyleConstants.setItalic(commentStyle, true);

        Style numberStyle = doc.addStyle(NUMBER_STYLE, defaultStyle);
        StyleConstants.setForeground(numberStyle, NUMBER_COLOR);

        Style stringStyle = doc.addStyle(STRING_STYLE, defaultStyle);
        StyleConstants.setForeground(stringStyle, STRING_COLOR);

        // Insert content with default style
        try {
            doc.insertString(0, content, defaultStyle);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        // Apply syntax highlighting
        highlightComments(doc, content, commentStyle);
        highlightKeywords(doc, content, keywordStyle);
        highlightNumbers(doc, content, numberStyle);
        highlightStrings(doc, content, stringStyle);
    }

    private void highlightComments(StyledDocument doc, String content, Style commentStyle) {
        commentRegions = new ArrayList<>();

        // Highlight single-line comments
        int pos = 0;
        while ((pos = content.indexOf("//", pos)) >= 0) {
            int endPos = content.indexOf("\n", pos);
            if (endPos < 0) endPos = content.length();
            doc.setCharacterAttributes(pos, endPos - pos, commentStyle, false);
            commentRegions.add(new int[]{pos, endPos});
            pos = endPos;
        }

        // Highlight multi-line comments
        pos = 0;
        while ((pos = content.indexOf("/*", pos)) >= 0) {
            int endPos = content.indexOf("*/", pos);
            if (endPos < 0) endPos = content.length();
            doc.setCharacterAttributes(pos, endPos - pos + 2, commentStyle, false);
            commentRegions.add(new int[]{pos, endPos + 2});
            pos = endPos + 2;
        }
    }

    private void highlightKeywords(StyledDocument doc, String content, Style keywordStyle) {
        // Highlight Java keywords
        String[] keywords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
                "new", "null", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
                "while"
        };

        // Join keywords into a regex pattern with word boundaries
        String keywordPattern = "\\b(" + String.join("|", keywords) + ")\\b";
        Pattern pattern = Pattern.compile(keywordPattern);
        Matcher matcher = pattern.matcher(content);

        // Apply styles for matched keywords
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (!isWithinComment(start, commentRegions)) {
                doc.setCharacterAttributes(start, end - start, keywordStyle, false);
            }
        }
    }

    private void highlightNumbers(StyledDocument doc, String content, Style numberStyle) {
        // Pattern to match numbers, integers and decimals
        String[] numberPatterns = {"\\b\\d+\\b", "\\b\\d+\\.\\d+\\b"};

        for (String patternString : numberPatterns) {
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(content);

            // Apply styles for matched keywords
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (!isWithinComment(start, commentRegions)) {
                    doc.setCharacterAttributes(start, end - start, numberStyle, false);
                }
            }
        }
    }

    private void highlightStrings(StyledDocument doc, String content, Style stringStyle) {
        Pattern pattern = Pattern.compile("\"(.*?)\"");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (!isWithinComment(start, commentRegions)) {
                doc.setCharacterAttributes(start, end - start, stringStyle, false);
            }
        }
    }

    private boolean isWithinComment(int position, ArrayList<int[]> commentRegions) {
        for (int[] region : commentRegions) {
            if (position >= region[0] && position < region[1]) {
                return true;
            }
        }

        return false;
    }
}
