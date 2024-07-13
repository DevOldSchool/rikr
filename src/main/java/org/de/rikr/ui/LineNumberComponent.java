package org.de.rikr.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class LineNumberComponent extends JComponent {
    private static final int MARGIN = 5;
    private final JTextPane textPane;
    private final Font defaultFont;
    private FontMetrics fontMetrics;
    private int fontLeading;
    private int fontDescent;
    private final int lineHeightOffset;

    public LineNumberComponent(JTextPane textPane) {
        this.textPane = textPane;

        defaultFont = Fonts.getDefaultFont();
        setFont(defaultFont);
        updateFontMetrics(defaultFont);

        textPane.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                repaint();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                repaint();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                repaint();
            }
        });

        textPane.addCaretListener(e -> repaint());
        textPane.addPropertyChangeListener("font", evt -> {
            Font newFont = (Font) evt.getNewValue();
            updateFontMetrics(newFont);
        });

        // TODO we shouldn't need this but struggling to get the line numbers to look good lined up to the content
        lineHeightOffset = 3;
    }

    private void updateFontMetrics(Font font) {
        fontMetrics = getFontMetrics(font);
        fontLeading = fontMetrics.getLeading();
        fontDescent = fontMetrics.getDescent();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Rectangle clip = g.getClipBounds();
        g.setColor(Theme.CONTENT_BACKGROUND_COLOR);
        g.fillRect(clip.x, clip.y, clip.width, clip.height);

        // Draw line highlight
        Rectangle2D caretRectangle;
        try {
            caretRectangle = textPane.modelToView2D(textPane.getCaretPosition());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        g.setColor(Theme.BACKGROUND_COLOR);
        g.fillRect(0, (int) caretRectangle.getY(), this.getWidth(), (int) caretRectangle.getHeight() + lineHeightOffset);

        // Draw line numbers
        g.setFont(defaultFont);

        int startOffset = textPane.viewToModel2D(new Point(0, clip.y));
        int endOffset = textPane.viewToModel2D(new Point(0, clip.y + clip.height));

        Element root = textPane.getDocument().getDefaultRootElement();
        int startLine = root.getElementIndex(startOffset);
        int endLine = root.getElementIndex(endOffset);

        for (int i = startLine; i <= endLine; i++) {
            g.setColor(Theme.CONTENT_TEXT_COLOR);

            try {
                Rectangle2D elementRectangle = textPane.modelToView2D(root.getElement(i).getStartOffset());

                if (elementRectangle != null) {
                    if (caretRectangle.getY() == elementRectangle.getY()) {
                        g.setColor(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
                    }
                    String lineNumber = String.valueOf(i + 1);
                    int y = (int) elementRectangle.getY() + (int) elementRectangle.getHeight() - fontDescent - fontLeading;
                    g.drawString(lineNumber, MARGIN, y + lineHeightOffset);
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }

        // Draw the right border
        g.setColor(Theme.CONTENT_BORDER_COLOR);
        int borderX = getWidth() - 1;
        g.drawLine(borderX, clip.y, borderX, clip.y + clip.height);

        int width = fontMetrics.stringWidth(String.valueOf(endLine + 1)) + MARGIN * 2;
        if (getPreferredWidth() != width) {
            setPreferredWidth(width);
        }
    }

    private int getPreferredWidth() {
        return fontMetrics.stringWidth("0000") + MARGIN * 2;
    }

    private void setPreferredWidth(int width) {
        Dimension dim = getPreferredSize();
        dim.setSize(width, dim.height);
        setPreferredSize(dim);
        setSize(dim);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getPreferredWidth(), textPane.getHeight());
    }
}