package org.de.rikr.ui;

import javax.swing.event.DocumentEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.util.Map;

/**
 * Shows a styled document of code as lines of one height that don't wrap, each drawn run by run in its own font and
 * color. Where a line or a character is follows from its line number and the widths of the text before it, so
 * showing, painting and finding a place cost the same in a class of a hundred lines as in one of a hundred
 * thousand. Swing's own styled views make a view for every line and every run and measure them all first, which
 * froze the window for seconds on large classes.
 * <p>
 * Line height comes from the first line's paragraph attributes: its font, the space above and below it, and its
 * line spacing, worked out as Swing's paragraphs do, so the text looks as it did.
 */
final class StyledLinesView extends View implements TabExpander {
    private static final int TAB_SIZE = 4;

    private final Segment segment = new Segment();
    private boolean measured;
    private FontMetrics metrics;
    private int spaceAbove;
    private int lineHeight;
    private int longestLine = -1;
    private float tabBase;

    StyledLinesView(Element section) {
        super(section);
    }

    /** Makes this view for a document's section, the element that holds its lines. */
    static ViewFactory factory() {
        return StyledLinesView::new;
    }

    private StyledDocument document() {
        return (StyledDocument) getDocument();
    }

    private void measure() {
        if (measured) {
            return;
        }
        Container host = getContainer();
        if (host == null) {
            return;
        }
        AttributeSet paragraph = getElement().getElementCount() > 0
            ? getElement().getElement(0).getAttributes() : getElement().getAttributes();
        metrics = host.getFontMetrics(document().getFont(paragraph));
        spaceAbove = (int) StyleConstants.getSpaceAbove(paragraph);
        int spaceBelow = (int) StyleConstants.getSpaceBelow(paragraph);
        int fontHeight = metrics.getHeight();
        // As ParagraphView does: a row is the font's height plus that times the line spacing, rounded towards 0.
        int rowHeight = fontHeight + (short) (fontHeight * StyleConstants.getLineSpacing(paragraph));
        lineHeight = Math.max(1, spaceAbove + rowHeight + spaceBelow);
        measured = true;
    }

    private void changed() {
        measured = false;
        longestLine = -1;
        preferenceChanged(null, true, true);
        Component host = getContainer();
        if (host != null) {
            host.repaint();
        }
    }

    @Override
    public void insertUpdate(DocumentEvent event, Shape allocation, ViewFactory factory) {
        changed();
    }

    @Override
    public void removeUpdate(DocumentEvent event, Shape allocation, ViewFactory factory) {
        changed();
    }

    @Override
    public void changedUpdate(DocumentEvent event, Shape allocation, ViewFactory factory) {
        changed();
    }

    @Override
    public float getPreferredSpan(int axis) {
        measure();
        if (metrics == null) {
            return 0;
        }
        Element root = getElement();
        if (axis == View.Y_AXIS) {
            return (float) root.getElementCount() * lineHeight;
        }
        if (longestLine < 0) {
            int longest = 0;
            for (int index = 0; index < root.getElementCount(); index++) {
                Element line = root.getElement(index);
                longest = Math.max(longest, line.getEndOffset() - line.getStartOffset());
            }
            longestLine = longest;
        }
        // A bold or wider character makes a line longer than this; a little room is left for it.
        return (float) longestLine * metrics.charWidth('m') * 1.1f;
    }

    @Override
    public void paint(Graphics graphics, Shape allocation) {
        measure();
        if (metrics == null) {
            return;
        }
        Rectangle bounds = allocation.getBounds();
        Rectangle clip = graphics.getClipBounds();
        if (clip == null) {
            clip = bounds;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            applyTextHints(g);
            Element root = getElement();
            int first = Math.max(0, (clip.y - bounds.y) / lineHeight);
            int last = Math.min(root.getElementCount() - 1, (clip.y + clip.height - bounds.y) / lineHeight);
            JTextComponent host = (JTextComponent) getContainer();
            Highlighter highlighter = host.getHighlighter();
            tabBase = bounds.x;
            for (int index = first; index <= last; index++) {
                Element line = root.getElement(index);
                if (highlighter instanceof LayeredHighlighter layered) {
                    layered.paintLayeredHighlights(g, line.getStartOffset(), line.getEndOffset(), allocation, host,
                        this);
                }
                paintLine(g, line, bounds.x, bounds.y + index * lineHeight + spaceAbove + metrics.getAscent());
            }
        } finally {
            g.dispose();
        }
    }

    private void paintLine(Graphics2D g, Element line, float x, int baseline) {
        StyledDocument document = document();
        float at = x;
        for (int index = 0; index < line.getElementCount(); index++) {
            Element run = line.getElement(index);
            int start = run.getStartOffset();
            int end = Math.min(run.getEndOffset(), line.getEndOffset() - 1);
            if (end <= start) {
                continue;
            }
            AttributeSet attributes = run.getAttributes();
            Color color = attributes.isDefined(StyleConstants.Foreground)
                ? document.getForeground(attributes) : getContainer().getForeground();
            g.setFont(document.getFont(attributes));
            g.setColor(color);
            if (!text(start, end)) {
                return;
            }
            at = Utilities.drawTabbedText(segment, at, baseline, g, this, start);
        }
    }

    // Text drawn here is drawn as the desktop draws text, as Swing's own views do.
    private static void applyTextHints(Graphics2D g) {
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map<?, ?> map) {
            g.addRenderingHints(map);
        } else {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    private boolean text(int start, int end) {
        try {
            getDocument().getText(start, end - start, segment);
            return true;
        } catch (BadLocationException ignored) {
            return false;
        }
    }

    // The width of a line's text from its start up to an offset in it.
    private float widthTo(Element line, int offset, float x) {
        StyledDocument document = document();
        Container host = getContainer();
        float at = x;
        for (int index = 0; index < line.getElementCount(); index++) {
            Element run = line.getElement(index);
            int start = run.getStartOffset();
            int end = Math.min(Math.min(run.getEndOffset(), line.getEndOffset() - 1), offset);
            if (end <= start || !text(start, end)) {
                break;
            }
            FontMetrics runMetrics = host.getFontMetrics(document.getFont(run.getAttributes()));
            at += Utilities.getTabbedTextWidth(segment, runMetrics, at, this, start);
        }
        return at - x;
    }

    @Override
    public Shape modelToView(int position, Shape allocation, Position.Bias bias) throws BadLocationException {
        if (position < getStartOffset() || position > getEndOffset()) {
            throw new BadLocationException("Not in the document", position);
        }
        measure();
        Rectangle bounds = allocation.getBounds();
        if (metrics == null) {
            return new Rectangle(bounds.x, bounds.y, 1, 1);
        }
        Element root = getElement();
        int index = root.getElementIndex(position);
        Element line = root.getElement(index);
        tabBase = bounds.x;
        int x = bounds.x + (int) widthTo(line, position, bounds.x);
        // The glyphs' own rectangle, as Swing's views give it: below the space above the line, the font's height.
        return new Rectangle(x, bounds.y + index * lineHeight + spaceAbove, 1, metrics.getHeight());
    }

    @Override
    public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
        measure();
        bias[0] = Position.Bias.Forward;
        Rectangle bounds = allocation.getBounds();
        Element root = getElement();
        if (metrics == null || root.getElementCount() == 0) {
            return getStartOffset();
        }
        int index = Math.max(0, Math.min(root.getElementCount() - 1, (int) ((y - bounds.y) / lineHeight)));
        Element line = root.getElement(index);
        int lineEnd = line.getEndOffset() - 1;
        if (y < bounds.y) {
            return line.getStartOffset();
        }
        tabBase = bounds.x;
        StyledDocument document = document();
        Container host = getContainer();
        float at = bounds.x;
        for (int run = 0; run < line.getElementCount(); run++) {
            Element element = line.getElement(run);
            int start = element.getStartOffset();
            int end = Math.min(element.getEndOffset(), lineEnd);
            if (end <= start || !text(start, end)) {
                continue;
            }
            FontMetrics runMetrics = host.getFontMetrics(document.getFont(element.getAttributes()));
            float width = Utilities.getTabbedTextWidth(segment, runMetrics, at, this, start);
            if (x < at + width) {
                return start + Utilities.getTabbedTextOffset(segment, runMetrics, at, x, this, start, true);
            }
            at += width;
        }
        return Math.max(line.getStartOffset(), lineEnd);
    }

    @Override
    public float nextTabStop(float x, int tabOffset) {
        float width = Math.max(1, metrics == null ? 8 : metrics.charWidth(' ') * TAB_SIZE);
        return tabBase + ((int) ((x - tabBase) / width) + 1) * width;
    }
}
