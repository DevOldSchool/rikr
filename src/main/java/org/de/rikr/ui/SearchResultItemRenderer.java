package org.de.rikr.ui;

import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.SearchResultItem;
import org.objectweb.asm.Type;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A search result row: the name or instruction that matched, with the matched text in bold. Members and
 * instructions sit under their class's row, so they don't repeat it; a class in a package names its package.
 */
public class SearchResultItemRenderer extends JLabel implements ListCellRenderer<SearchResultItem> {
    // A type descriptor such as Ljava/applet/Applet; or [I, shown as people write the type.
    private static final Pattern DESCRIPTOR = Pattern.compile("\\[*(L[^;\\s]+;|[ZBCSIJFD])");

    private final Supplier<String> query;
    private final BooleanSupplier matchCase;

    /**
     * @param query     the text searched for, to show in bold
     * @param matchCase whether that search matched case
     */
    public SearchResultItemRenderer(Supplier<String> query, BooleanSupplier matchCase) {
        this.query = query;
        this.matchCase = matchCase;
    }

    private static String toHex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends SearchResultItem> list, SearchResultItem value, int index, boolean isSelected, boolean cellHasFocus) {
        int indent = Math.max(0, value.getIndentLevel());
        setBorder(BorderFactory.createEmptyBorder(0, indent * 16, 0, 0));
        if (value.getClassNode() == null) {
            // A JAR heading when results come from more than one JAR.
            setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
            setText("<html><b>" + escape(value.getText()) + "</b></html>");
            setIcon(null);
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            setFont(list.getFont());
            setOpaque(true);
            return this;
        }
        String primary = isSelected ? toHex(list.getSelectionForeground()) : toHex(Theme.CONTENT_TEXT_COLOR);
        String secondary = SecondaryText.color(list, isSelected);
        String text = readable(value.getText());
        String html = "<html><span style='color:" + primary + "'>" + emphasise(text) + "</span>";
        String owner = value.getClassNode().name;
        int slash = owner.lastIndexOf('/');
        if (indent == 0 && slash >= 0) {
            html += " <span style='color:" + secondary + "'>in " + escape(owner.substring(0, slash).replace('/', '.'))
                + "</span>";
        }
        setText(html + "</html>");
        setIcon(value.getIcon());

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        setEnabled(list.isEnabled());
        setFont(list.getFont());
        setOpaque(true);

        return this;
    }

    static String readable(String text) {
        if (!DESCRIPTOR.matcher(text).matches()) {
            return text;
        }
        return Type.getType(text).getClassName();
    }

    // The text with each occurrence of the query in bold, escaped for HTML.
    private String emphasise(String text) {
        String searched = query.get();
        if (searched == null || searched.isEmpty()) {
            return escape(text);
        }
        boolean exact = matchCase.getAsBoolean();
        String haystack = exact ? text : text.toLowerCase(Locale.ROOT);
        String needle = exact ? searched : searched.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int from = 0;
        int at;
        while ((at = haystack.indexOf(needle, from)) >= 0) {
            out.append(escape(text.substring(from, at)))
                .append("<b>").append(escape(text.substring(at, at + needle.length()))).append("</b>");
            from = at + needle.length();
        }
        return out.append(escape(text.substring(from))).toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
