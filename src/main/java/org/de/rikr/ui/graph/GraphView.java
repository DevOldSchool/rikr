package org.de.rikr.ui.graph;

import org.de.rikr.flow.GraphLayout;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a {@link DrawnGraph} laid out in layers, in a scroll pane. Drag to pan, Ctrl or Cmd with the wheel to zoom,
 * click to select, double-click or right-click to act on a node, and click a node's badge to expand it.
 */
public class GraphView extends JScrollPane {
    private static final int PADDING = 8;
    private static final int MAX_LINE_CHARS = 72;
    private static final double MIN_SCALE = 0.05;
    private static final double MAX_SCALE = 2.5;
    // Fitting never shrinks text below this, so a large graph is fitted to its width and scrolled instead.
    private static final double MIN_READABLE_SCALE = 0.5;
    // Below this many pixels per line of text on screen, blocks show their title only, drawn large enough to read.
    private static final double MIN_TEXT_PIXELS = 9;
    private static final double TITLE_ONLY_PIXELS = 11;
    // Text is measured and drawn with fractional metrics, so its width scales exactly with the zoom and stays
    // inside its box at every zoom level.
    private static final java.awt.font.FontRenderContext TEXT = new java.awt.font.FontRenderContext(null, true, true);

    /**
     * What happens when a node is used.
     */
    public interface Listener {
        void doubleClicked(int node);

        void badgeClicked(int node);

        /**
         * The menu for a node, or null for none.
         */
        JPopupMenu menu(int node);

        void zoomChanged(double scale);
    }

    private final Canvas canvas = new Canvas();
    private final Overview overview = new Overview();
    private final Listener listener;
    private DrawnGraph graph;
    private GraphLayout.Result layout;
    private List<String[]> shownLines = List.of();
    private double scale = 1;
    private int selected = -1;

    public GraphView(Listener listener) {
        this.listener = listener;
        setViewportView(canvas);
        setBorder(null);
        getVerticalScrollBar().setUnitIncrement(24);
        getHorizontalScrollBar().setUnitIncrement(24);
        ToolTipManager.sharedInstance().registerComponent(canvas);
        getViewport().addChangeListener(event -> overview.update());
        applyTheme();
        SettingsManager.addListener(settings -> {
            applyTheme();
            if (graph != null) {
                show(graph, true);
            }
        });
    }

    public double scale() {
        return scale;
    }

    public int selected() {
        return selected;
    }

    /**
     * Draw a graph. With {@code keepView} the zoom and scroll position stay, for a graph that only grew or was
     * redrawn; otherwise it is fitted to the window.
     */
    public void show(DrawnGraph drawn, boolean keepView) {
        show(drawn, keepView, Integer.MAX_VALUE);
    }

    /**
     * @param maxPerRow the most nodes side by side before a layer wraps onto another row
     */
    public void show(DrawnGraph drawn, boolean keepView, int maxPerRow) {
        graph = drawn;
        if (!keepView) {
            selected = -1;
        } else if (selected >= drawn.nodes().size()) {
            selected = -1;
        }
        FontMetrics metrics = canvas.getFontMetrics(font());
        Font font = font();
        List<Dimension> sizes = new ArrayList<>();
        List<String[]> lines = new ArrayList<>();
        for (DrawnGraph.Node node : drawn.nodes()) {
            String[] text = node.lines().stream()
                .map(line -> line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS - 1) + "…" : line)
                .toArray(String[]::new);
            lines.add(text);
            int width = 0;
            for (int index = 0; index < text.length; index++) {
                int extra = index == 0 && node.badge() != null ? textWidth(font, node.badge()) + 2 * PADDING : 0;
                width = Math.max(width, textWidth(font, text[index]) + extra);
            }
            sizes.add(new Dimension(width + 2 * PADDING, text.length * metrics.getHeight() + 2 * PADDING));
        }
        shownLines = lines;
        layout = GraphLayout.layout(drawn.nodes().size(),
            drawn.edges().stream().map(edge -> new GraphLayout.Link(edge.from(), edge.to())).toList(), sizes,
            maxPerRow);
        canvas.revalidate();
        canvas.repaint();
        if (!keepView) {
            SwingUtilities.invokeLater(this::fit);
        }
    }

    public void clear() {
        graph = null;
        layout = null;
        selected = -1;
        canvas.revalidate();
        canvas.repaint();
    }

    public void select(int node) {
        selected = node;
        canvas.repaint();
    }

    /**
     * Fit the graph to the window, or, when that would make its text too small to read, fit its width (but no
     * smaller than half size) and show the top, where the graph starts.
     */
    public void fit() {
        if (layout == null) {
            return;
        }
        Dimension view = getViewport().getExtentSize();
        double whole = Math.min((double) view.width / layout.width(), (double) view.height / layout.height());
        if (whole >= MIN_READABLE_SCALE) {
            zoomTo(Math.min(1, whole), null);
            return;
        }
        double width = Math.max(MIN_READABLE_SCALE, Math.min(1, (double) view.width / layout.width()));
        zoomTo(width, null);
        Rectangle start = layout.boxes().isEmpty() ? new Rectangle() : layout.boxes().getFirst();
        int x = (int) Math.round((start.x + start.width / 2.0) * width - view.width / 2.0);
        getViewport().setViewPosition(new Point(Math.max(0, Math.min(x, canvas.getWidth() - view.width)), 0));
    }

    /**
     * Select the next node, after the selected one, with a line containing {@code text}, ignoring case, and scroll
     * to it.
     *
     * @return false if no node contains it
     */
    public boolean findNext(String text) {
        if (graph == null || text.isBlank()) {
            return false;
        }
        String wanted = text.strip().toLowerCase(java.util.Locale.ROOT);
        int count = graph.nodes().size();
        for (int step = 1; step <= count; step++) {
            int node = Math.floorMod(selected + step, count);
            boolean matches = graph.nodes().get(node).lines().stream()
                .anyMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains(wanted));
            if (matches) {
                selected = node;
                if (scale < MIN_READABLE_SCALE) {
                    zoomTo(MIN_READABLE_SCALE, null);
                }
                Rectangle box = layout.boxes().get(node);
                canvas.scrollRectToVisible(new Rectangle((int) (box.x * scale) - 40, (int) (box.y * scale) - 40,
                    (int) (box.width * scale) + 80, (int) (box.height * scale) + 80));
                canvas.repaint();
                return true;
            }
        }
        return false;
    }

    /**
     * A small map of the whole graph with the visible part outlined, shown while the graph is larger than the
     * window. Click or drag in it to move there.
     */
    public JComponent overview() {
        return overview;
    }

    private static int textWidth(Font font, String text) {
        return (int) Math.ceil(font.getStringBounds(text, TEXT).getWidth());
    }

    public void zoomIn() {
        zoomTo(scale * 1.25, null);
    }

    public void zoomOut() {
        zoomTo(scale / 1.25, null);
    }

    /**
     * Zoom, keeping {@code anchor} (a point of the canvas, at the current scale) where it is on screen.
     */
    private void zoomTo(double newScale, Point anchor) {
        double clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        JViewport viewport = getViewport();
        Point view = viewport.getViewPosition();
        Point focus = anchor != null ? anchor
            : new Point(view.x + viewport.getWidth() / 2, view.y + viewport.getHeight() / 2);
        double ratio = clamped / scale;
        scale = clamped;
        canvas.revalidate();
        viewport.doLayout();
        int x = (int) Math.round(focus.x * ratio - (focus.x - view.x));
        int y = (int) Math.round(focus.y * ratio - (focus.y - view.y));
        viewport.setViewPosition(new Point(Math.max(0, x), Math.max(0, y)));
        canvas.repaint();
        listener.zoomChanged(scale);
        overview.update();
    }

    private Font font() {
        Font editor = SettingsManager.getEditorFont();
        return editor.deriveFont(Math.max(10f, editor.getSize2D() - 1));
    }

    private void applyTheme() {
        getViewport().setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        canvas.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        repaint();
    }

    private int nodeAt(Point point) {
        if (layout == null) {
            return -1;
        }
        double x = point.x / scale;
        double y = point.y / scale;
        for (int index = 0; index < layout.boxes().size(); index++) {
            if (layout.boxes().get(index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    private Rectangle badgeBounds(int node) {
        String badge = graph.nodes().get(node).badge();
        if (badge == null) {
            return null;
        }
        FontMetrics metrics = canvas.getFontMetrics(font());
        Rectangle box = layout.boxes().get(node);
        int width = textWidth(font(), badge) + PADDING;
        return new Rectangle(box.x + box.width - width - PADDING / 2, box.y + PADDING / 2, width,
            metrics.getHeight());
    }

    private final class Canvas extends JComponent {
        private Point dragStart;

        Canvas() {
            setOpaque(true);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    dragStart = event.getLocationOnScreen();
                    if (event.isPopupTrigger()) {
                        showMenu(event);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (event.isPopupTrigger()) {
                        showMenu(event);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (dragStart == null) {
                        return;
                    }
                    Point now = event.getLocationOnScreen();
                    JViewport viewport = getViewport();
                    Point view = viewport.getViewPosition();
                    int maxX = Math.max(0, getWidth() - viewport.getWidth());
                    int maxY = Math.max(0, getHeight() - viewport.getHeight());
                    viewport.setViewPosition(new Point(
                        Math.max(0, Math.min(maxX, view.x - (now.x - dragStart.x))),
                        Math.max(0, Math.min(maxY, view.y - (now.y - dragStart.y)))));
                    dragStart = now;
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) {
                        return;
                    }
                    int node = nodeAt(event.getPoint());
                    selected = node;
                    repaint();
                    if (node < 0) {
                        return;
                    }
                    Rectangle badge = badgeBounds(node);
                    if (badge != null && badge.contains(event.getX() / scale, event.getY() / scale)) {
                        listener.badgeClicked(node);
                    } else if (event.getClickCount() == 2) {
                        listener.doubleClicked(node);
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent event) {
                    if (event.isControlDown() || event.isMetaDown()) {
                        zoomTo(scale * Math.pow(1.1, -event.getPreciseWheelRotation()), event.getPoint());
                    } else {
                        getParent().dispatchEvent(SwingUtilities.convertMouseEvent(Canvas.this, event, getParent()));
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        private void showMenu(MouseEvent event) {
            int node = nodeAt(event.getPoint());
            if (node < 0) {
                return;
            }
            selected = node;
            repaint();
            JPopupMenu menu = listener.menu(node);
            if (menu != null) {
                menu.show(this, event.getX(), event.getY());
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (layout == null) {
                return new Dimension(1, 1);
            }
            return new Dimension((int) Math.ceil(layout.width() * scale), (int) Math.ceil(layout.height() * scale));
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int node = nodeAt(event.getPoint());
            if (node < 0 || graph.nodes().get(node).tooltip() == null) {
                return null;
            }
            String text = graph.nodes().get(node).tooltip()
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            return "<html><pre>" + text + "</pre></html>";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(Theme.CONTENT_BACKGROUND_COLOR);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (layout == null) {
                g.dispose();
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.scale(scale, scale);
            g.setFont(font());
            for (int index = 0; index < graph.edges().size(); index++) {
                paintEdge(g, index);
            }
            FontMetrics metrics = g.getFontMetrics();
            boolean titlesOnly = scale * metrics.getHeight() < MIN_TEXT_PIXELS;
            for (int index = 0; index < layout.boxes().size(); index++) {
                paintNode(g, index, metrics, titlesOnly);
            }
            g.dispose();
        }

        private void paintNode(Graphics2D g, int index, FontMetrics metrics, boolean titlesOnly) {
            Rectangle box = layout.boxes().get(index);
            DrawnGraph.Node node = graph.nodes().get(index);
            g.setColor(Theme.BACKGROUND_COLOR);
            g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
            boolean isSelected = index == selected;
            g.setColor(isSelected || node.marked() ? Theme.SEARCH_BUTTON_SELECTED_COLOR
                : node.dim() ? Theme.CONTENT_HIGHLIGHT_TEXT_COLOR : Theme.CONTENT_TEXT_COLOR);
            g.setStroke(isSelected || node.marked() ? new BasicStroke(2.5f)
                : node.dim() ? new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[]{4f, 4f}, 0f)
                : new BasicStroke(1f));
            g.drawRoundRect(box.x, box.y, box.width, box.height, 8, 8);
            String[] lines = shownLines.get(index);
            Rectangle badge = badgeBounds(index);
            if (titlesOnly) {
                paintTitleOnly(g, box, node, lines[0]);
                return;
            }
            int y = box.y + PADDING + metrics.getAscent();
            for (int line = 0; line < lines.length; line++) {
                g.setColor(line == 0 ? node.titleColor()
                    : node.dim() ? Theme.SYNTAX_HIGHLIGHT_COMMENT_COLOR : Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR);
                g.drawString(lines[line], box.x + PADDING, y);
                y += metrics.getHeight();
            }
            if (badge != null) {
                g.setColor(Theme.CONTENT_SELECTED_HIGHLIGHT_COLOR);
                g.fillRoundRect(badge.x, badge.y, badge.width, badge.height, 6, 6);
                g.setColor(Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR);
                g.drawString(node.badge(), badge.x + PADDING / 2, badge.y + metrics.getAscent());
            }
        }

        /**
         * Zoomed out until the text would be unreadable, a block shows its title alone, at a size that reads on
         * screen, so the graph stays a map rather than a picture.
         */
        private void paintTitleOnly(Graphics2D g, Rectangle box, DrawnGraph.Node node, String title) {
            Font base = g.getFont();
            Font large = base.deriveFont((float) (TITLE_ONLY_PIXELS / scale));
            FontMetrics metrics = g.getFontMetrics(large);
            int room = box.width - 2 * PADDING;
            // A box narrower than the readable size holds the word at its own width instead.
            if (metrics.getHeight() > box.height || metrics.stringWidth("W") > room) {
                large = base.deriveFont((float) Math.max(base.getSize2D(),
                    Math.min(box.height, room / 3) * 0.8));
                metrics = g.getFontMetrics(large);
            }
            String text = elide(title, metrics, room);
            g.setFont(large);
            g.setColor(node.titleColor());
            int x = box.x + (box.width - metrics.stringWidth(text)) / 2;
            int y = box.y + (box.height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, Math.max(box.x + PADDING / 2, x), y);
            g.setFont(base);
        }

        private void paintEdge(Graphics2D g, int index) {
            DrawnGraph.Edge edge = graph.edges().get(index);
            List<Point> route = layout.routes().get(index);
            boolean touchesSelection = edge.from() == selected || edge.to() == selected;
            Color color = edge.color();
            float width = touchesSelection ? 2.5f : 1.2f;
            g.setColor(selected >= 0 && !touchesSelection ? withAlpha(color, 90) : color);
            g.setStroke(edge.dashed()
                ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 4f}, 0f)
                : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D path = new Path2D.Double();
            path.moveTo(route.getFirst().x, route.getFirst().y);
            for (Point point : route.subList(1, route.size())) {
                path.lineTo(point.x, point.y);
            }
            g.draw(path);
            paintArrowHead(g, route.get(route.size() - 2), route.getLast());
            if (edge.label() != null) {
                Point at = route.get(route.size() - 2);
                g.drawString(edge.label(), at.x + 4, at.y - 3);
            }
            if (edge.startLabel() != null && route.size() >= 2) {
                paintStartLabel(g, edge.startLabel(), route.get(0), route.get(1));
            }
        }

        // The outcome a branch edge stands for, where it leaves its block, on a patch of background so the lines
        // around it don't cut through the letter. Colour alone would say nothing to a colour-blind reader.
        private void paintStartLabel(Graphics2D g, String text, Point from, Point next) {
            double dx = next.x - from.x;
            double dy = next.y - from.y;
            double length = Math.max(1, Math.hypot(dx, dy));
            Font base = g.getFont();
            Font bold = base.deriveFont(Font.BOLD);
            FontMetrics metrics = g.getFontMetrics(bold);
            // A little way along the edge, offset to the right of its direction.
            double along = Math.min(14, length / 2);
            int x = (int) Math.round(from.x + dx / length * along - dy / length * 7);
            int y = (int) Math.round(from.y + dy / length * along + dx / length * 7);
            int width = metrics.stringWidth(text);
            int height = metrics.getAscent();
            Color line = g.getColor();
            g.setColor(Theme.CONTENT_BACKGROUND_COLOR);
            g.fillRoundRect(x - width / 2 - 2, y - height / 2 - 1, width + 4, height + 2, 4, 4);
            g.setColor(line);
            g.setFont(bold);
            g.drawString(text, x - width / 2, y + height / 2 - 1);
            g.setFont(base);
        }

        // A title too long for its box ends in an ellipsis, so a cut word is never mistaken for a whole one.
        private String elide(String text, FontMetrics metrics, int room) {
            if (metrics.stringWidth(text) <= room) {
                return text;
            }
            int end = text.length();
            while (end > 1 && metrics.stringWidth(text.substring(0, end) + "…") > room) {
                end--;
            }
            return text.substring(0, end) + "…";
        }

        private void paintArrowHead(Graphics2D g, Point from, Point to) {
            double angle = Math.atan2(to.y - from.y, to.x - from.x);
            double size = 7;
            Path2D head = new Path2D.Double();
            head.moveTo(to.x, to.y);
            head.lineTo(to.x - size * Math.cos(angle - 0.4), to.y - size * Math.sin(angle - 0.4));
            head.lineTo(to.x - size * Math.cos(angle + 0.4), to.y - size * Math.sin(angle + 0.4));
            head.closePath();
            g.setStroke(new BasicStroke(1f));
            g.fill(head);
        }

        private Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }

    // A JPanel, since a bare JComponent has no accessible context to name.
    private final class Overview extends JPanel {
        private static final int SIZE = 180;

        Overview() {
            setOpaque(false);
            setVisible(false);
            setToolTipText("Overview: click or drag to move around the graph");
            getAccessibleContext().setAccessibleName("Graph overview");
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    centreOn(event.getPoint());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    centreOn(event.getPoint());
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        void update() {
            boolean larger = layout != null
                && (canvas.getWidth() > getViewport().getWidth() || canvas.getHeight() > getViewport().getHeight());
            setVisible(larger);
            repaint();
        }

        private double ratio() {
            return Math.min((double) (SIZE - 8) / layout.width(), (double) (SIZE - 8) / layout.height());
        }

        private void centreOn(Point point) {
            if (layout == null) {
                return;
            }
            double ratio = ratio();
            JViewport viewport = getViewport();
            int x = (int) Math.round((point.x - 4) / ratio * scale - viewport.getWidth() / 2.0);
            int y = (int) Math.round((point.y - 4) / ratio * scale - viewport.getHeight() / 2.0);
            viewport.setViewPosition(new Point(
                Math.max(0, Math.min(x, canvas.getWidth() - viewport.getWidth())),
                Math.max(0, Math.min(y, canvas.getHeight() - viewport.getHeight()))));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (layout == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(Theme.BACKGROUND_COLOR);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g.setColor(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            double ratio = ratio();
            for (int index = 0; index < layout.boxes().size(); index++) {
                Rectangle box = layout.boxes().get(index);
                g.setColor(index == selected ? Theme.SEARCH_BUTTON_SELECTED_COLOR : Theme.CONTENT_TEXT_COLOR);
                g.fillRect(4 + (int) (box.x * ratio), 4 + (int) (box.y * ratio),
                    Math.max(1, (int) (box.width * ratio)), Math.max(1, (int) (box.height * ratio)));
            }
            Rectangle visible = getViewport().getViewRect();
            g.setColor(Theme.SEARCH_BUTTON_SELECTED_COLOR);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(4 + (int) (visible.x / scale * ratio), 4 + (int) (visible.y / scale * ratio),
                (int) (visible.width / scale * ratio), (int) (visible.height / scale * ratio));
            g.dispose();
        }
    }
}
