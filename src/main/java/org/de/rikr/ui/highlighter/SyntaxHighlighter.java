package org.de.rikr.ui.highlighter;

import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.text.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {
    private static final Logger LOGGER = Logger.getLogger(SyntaxHighlighter.class.getName());


    private static final float LINE_HEIGHT = -0.2f;

    private static Font defaultFont;
    private ArrayList<int[]> commentRegions;
    private ArrayList<int[]> stringRegions;

    public SyntaxHighlighter() {
        defaultFont = SettingsManager.getEditorFont();
    }

    public void highlight(StyledDocument doc, String content) {
        // Refresh font from settings on each highlight
        defaultFont = SettingsManager.getEditorFont();
        // Clear existing content and styles
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        // Plain attribute sets, not the document's styles: the document is highlighted off the event thread, and a
        // change to one of its styles is announced to the screen later, as a change to all of its text.
        SimpleAttributeSet defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR);
        StyleConstants.setFontFamily(defaultStyle, defaultFont.getFamily());
        StyleConstants.setFontSize(defaultStyle, defaultFont.getSize());
        StyleConstants.setLineSpacing(defaultStyle, LINE_HEIGHT);
        StyleConstants.setSpaceAbove(defaultStyle, 4);
        StyleConstants.setSpaceBelow(defaultStyle, 4);

        // Applied over the default style, which every character already has.
        SimpleAttributeSet keywordStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(keywordStyle, Theme.SYNTAX_HIGHLIGHT_KEYWORD_COLOR);
        StyleConstants.setBold(keywordStyle, true);

        SimpleAttributeSet commentStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(commentStyle, Theme.SYNTAX_HIGHLIGHT_COMMENT_COLOR);
        StyleConstants.setItalic(commentStyle, true);

        SimpleAttributeSet numberStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(numberStyle, Theme.SYNTAX_HIGHLIGHT_NUMBER_COLOR);

        SimpleAttributeSet stringStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(stringStyle, Theme.SYNTAX_HIGHLIGHT_STRING_COLOR);

        SimpleAttributeSet annotationStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(annotationStyle, Theme.SYNTAX_HIGHLIGHT_ANNOTATION_COLOR);
        StyleConstants.setBold(annotationStyle, true);

        // Insert content with default style
        try {
            doc.insertString(0, content, defaultStyle);
        } catch (BadLocationException e) {
            LOGGER.log(Level.WARNING, "Failed to insert highlighted content.", e);
        }

        // Apply the default style to the entire document
        doc.setParagraphAttributes(0, doc.getLength(), defaultStyle, false);

        // Apply syntax highlighting (order matters)
        highlightComments(doc, content, commentStyle);
        highlightStrings(doc, content, stringStyle);
        highlightAnnotations(doc, content, annotationStyle);
        highlightKeywords(doc, content, keywordStyle);
        highlightNumbers(doc, content, numberStyle);
    }

    private void highlightComments(StyledDocument doc, String content, AttributeSet commentStyle) {
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

    private void highlightKeywords(StyledDocument doc, String content, AttributeSet keywordStyle) {
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

    private void highlightNumbers(StyledDocument doc, String content, AttributeSet numberStyle) {
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

    private void highlightStrings(StyledDocument doc, String content, AttributeSet stringStyle) {
        stringRegions = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"(.*?)\"");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (!isWithinComment(start, commentRegions)) {
                doc.setCharacterAttributes(start, end - start, stringStyle, false);
                stringRegions.add(new int[]{start, end});
            }
        }
    }

    private void highlightAnnotations(StyledDocument doc, String content, AttributeSet annotationStyle) {
        // Patterns:
        // 1) Java-style annotations: @Qualified.OrSimple_Name
        // 2) JVM descriptor annotations as in examples: @Lorg/de/mapping/Name;
        String javaStyle = "@(?:[A-Za-z_$][A-Za-z0-9_$]*)(?:\\.(?:[A-Za-z_$][A-Za-z0-9_$]*))*";
        String jvmDescriptor = "@L[0-9A-Za-z_/$]+;";
        Pattern pattern = Pattern.compile(jvmDescriptor + "|" + javaStyle);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (!isWithinComment(start, commentRegions) && !isWithinString(start, stringRegions)) {
                doc.setCharacterAttributes(start, end - start, annotationStyle, false);
            }
        }
    }

    private boolean isWithinComment(int position, ArrayList<int[]> commentRegions) {
        return isWithinRegions(position, commentRegions);
    }

    private boolean isWithinString(int position, ArrayList<int[]> stringRegions) {
        return isWithinRegions(position, stringRegions);
    }

    private boolean isWithinRegions(int position, ArrayList<int[]> regions) {
        if (regions == null) return false;
        for (int[] region : regions) {
            if (position >= region[0] && position < region[1]) {
                return true;
            }
        }

        return false;
    }
}
