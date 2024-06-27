package org.de.rikr.ui.highlighter;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;

public class LineHighlighter implements Highlighter.HighlightPainter, CaretListener, MouseListener, MouseMotionListener, FocusListener {
    private final JTextComponent component;
    private Color color;
    private Rectangle2D lastView = new Rectangle(0, 0, 5, 5);

    public LineHighlighter(JTextComponent component, Color color) {
        this.component = component;
        setColor(color);

        component.addCaretListener(this);
        component.addMouseListener(this);
        component.addMouseMotionListener(this);
        component.addFocusListener(this);
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
        Rectangle2D rectangle;
        try {
            rectangle = c.modelToView2D(c.getCaretPosition());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }

        g.setColor(color);
        g.fillRect(0, (int) rectangle.getY(), c.getWidth(), (int) rectangle.getHeight());

        if (lastView == null) {
            lastView = rectangle;
        }
    }

    private void resetHighlight() {
        SwingUtilities.invokeLater(() -> {
            int offset = component.getCaretPosition();
            Rectangle2D currentView;

            try {
                currentView = component.modelToView2D(offset);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }

            // Remove the highlighting from the previously highlighted line
            if (lastView.getY() != currentView.getY()) {
                component.repaint(0, (int) lastView.getY(), component.getWidth(), (int) lastView.getHeight());
                lastView = currentView;
            }
        });
    }

    @Override
    public void caretUpdate(CaretEvent e) {
        resetHighlight();
    }

    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        resetHighlight();
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        resetHighlight();
    }

    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void focusGained(FocusEvent e) {
        try {
            component.getHighlighter().addHighlight(0, 0, this);
        } catch (BadLocationException badLocationException) {
            throw new RuntimeException(badLocationException);
        }
    }

    @Override
    public void focusLost(FocusEvent e) {
        component.getHighlighter().removeAllHighlights();
    }
}
