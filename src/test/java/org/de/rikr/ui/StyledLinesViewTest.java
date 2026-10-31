package org.de.rikr.ui;

import org.junit.jupiter.api.Test;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyledLinesViewTest {
    private static final String TEXT = "public final class a {\n   static int b;\n\n   long c = 1L;\n}\n";

    @Test
    void placesLinesAtOneHeightAndFindsCharactersBothWays() throws BadLocationException {
        ScrollableTextPane pane = pane(TEXT);
        Rectangle first = pane.modelToView2D(0).getBounds();
        Rectangle second = pane.modelToView2D(TEXT.indexOf("static")).getBounds();
        Rectangle fourth = pane.modelToView2D(TEXT.indexOf("long")).getBounds();

        int lineHeight = second.y - first.y;
        assertTrue(lineHeight > 0);
        assertEquals(second.y + 2 * lineHeight, fourth.y);
        assertTrue(second.x > first.x, "indented");
        for (String word : new String[]{"final", "static", "b;", "1L", "}"}) {
            int offset = TEXT.indexOf(word);
            Rectangle at = pane.modelToView2D(offset).getBounds();
            assertEquals(offset, pane.viewToModel2D(new Point(at.x + 1, at.y + at.height / 2)), word);
        }
    }

    @Test
    void measuresBoldRunsInTheirOwnFont() throws BadLocationException {
        ScrollableTextPane pane = pane(TEXT);
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        ((DefaultStyledDocument) pane.getDocument()).setCharacterAttributes(0, "public".length(), bold, false);

        int offset = TEXT.indexOf("class");
        Rectangle at = pane.modelToView2D(offset).getBounds();
        assertEquals(offset, pane.viewToModel2D(new Point(at.x + 1, at.y + at.height / 2)));
    }

    @Test
    void isAsTallAsItsLines() throws BadLocationException {
        ScrollableTextPane pane = pane(TEXT);
        int lineHeight = pane.modelToView2D(TEXT.indexOf("static")).getBounds().y - pane.modelToView2D(0).getBounds().y;
        int lines = pane.getDocument().getDefaultRootElement().getElementCount();

        View root = pane.getUI().getRootView(pane);
        assertEquals(lines * lineHeight, (int) root.getPreferredSpan(View.Y_AXIS));
    }

    private static ScrollableTextPane pane(String text) throws BadLocationException {
        ScrollableTextPane pane = new ScrollableTextPane();
        pane.getDocument().insertString(0, text, null);
        pane.setSize(400, 400);
        return pane;
    }
}
