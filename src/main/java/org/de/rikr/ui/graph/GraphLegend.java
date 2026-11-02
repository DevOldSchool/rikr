package org.de.rikr.ui.graph;

import org.de.rikr.theme.Theme;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * What the edge colours and dashes of a graph mean, drawn as short line samples with a word each, beside the
 * graph's title. Hidden while there is nothing to explain. A JPanel, since a bare JComponent has no accessible
 * context to name.
 */
public class GraphLegend extends JPanel {
    private static final int SAMPLE = 22;
    private static final int GAP = 14;

    /**
     * @param dashed drawn as a dashed line, as the edges it explains are
     */
    public record Item(Color color, boolean dashed, String text) {
    }

    private List<Item> items = List.of();

    public GraphLegend() {
        setOpaque(false);
        getAccessibleContext().setAccessibleName("Edge legend");
    }

    public void show(List<Item> shown) {
        items = List.copyOf(shown);
        setVisible(!items.isEmpty());
        setToolTipText(items.isEmpty() ? null : "What the arrows mean: "
            + String.join(", ", items.stream().map(Item::text).toList()));
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = 8;
        for (Item item : items) {
            width += SAMPLE + 6 + metrics.stringWidth(item.text()) + GAP;
        }
        return new Dimension(width, metrics.getHeight() + 6);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = getFont();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int x = 4;
        int middle = getHeight() / 2;
        int baseline = middle + metrics.getAscent() / 2 - 1;
        for (Item item : items) {
            g.setColor(item.color());
            g.setStroke(item.dashed()
                ? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 4f}, 0f)
                : new BasicStroke(2f));
            g.drawLine(x, middle, x + SAMPLE, middle);
            x += SAMPLE + 6;
            g.setColor(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
            g.drawString(item.text(), x, baseline);
            x += metrics.stringWidth(item.text()) + GAP;
        }
        g.dispose();
    }
}
