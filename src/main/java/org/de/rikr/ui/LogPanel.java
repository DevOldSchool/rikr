package org.de.rikr.ui;

import org.de.rikr.Logger;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Every message Rikr reports, with the time it was reported. The header filters the lines shown, copies them and
 * clears the log.
 */
public class LogPanel extends JPanel implements Logger {
    private final List<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();
    private static final String ATTR_KIND = "logKind";
    private static final String KIND_TIMESTAMP = "timestamp";
    private static final String KIND_MESSAGE = "message";

    private record Entry(String timestamp, String message) {
    }

    // Kept apart from the document so a filter can show a subset and clearing it brings every line back.
    private final List<Entry> entries = new ArrayList<>();
    private final JScrollPane scroll = new JScrollPane();
    private final JTextPane logPane;
    private final StyledDocument document;
    private final JTextField filter = new JTextField(18);
    private final JLabel title = new JLabel("Log");
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JButton hide = new JButton("Hide");
    private Runnable hideAction = () -> setVisible(false);

    private final DateFormat dateFormat;

    public LogPanel() {
        super(new BorderLayout());
        scroll.setBorder(null);

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.getAccessibleContext().setAccessibleName("Log");
        scroll.setViewportView(logPane);

        document = logPane.getStyledDocument();
        dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        title.setFont(title.getFont().deriveFont(Font.BOLD));
        SearchFields.style(filter, "Filter the log");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                render();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                render();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                render();
            }
        });
        JButton copy = new JButton("Copy");
        copy.setToolTipText("Copy the selected text, or every line shown, to the clipboard");
        copy.addActionListener(event -> copyShown());
        JButton clear = new JButton("Clear");
        clear.setToolTipText("Remove every message from the log");
        clear.addActionListener(event -> clear());
        hide.setToolTipText("Hide the log (Cmd+L)");
        hide.addActionListener(event -> hideAction.run());
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        tools.setOpaque(false);
        tools.add(filter);
        tools.add(copy);
        tools.add(clear);
        tools.add(hide);
        header.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        header.add(title, BorderLayout.WEST);
        header.add(tools, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    /**
     * What Hide does: the window takes the log out of its layout, as Window › Show or Hide Log does.
     */
    public void setHideAction(Runnable hideAction) {
        this.hideAction = hideAction;
    }

    private void applyTheme() {
        try {
            setBackground(Theme.BACKGROUND_COLOR);
            header.setBackground(Theme.BACKGROUND_COLOR);
            header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.CONTENT_BORDER_COLOR),
                BorderFactory.createEmptyBorder(4, 10, 4, 6)));
            logPane.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
            logPane.setForeground(Theme.CONTENT_TEXT_COLOR);

            restyleExistingText();

            JScrollBar vsb = scroll.getVerticalScrollBar();
            if (vsb != null) {
                vsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }

            JScrollBar hsb = scroll.getHorizontalScrollBar();
            if (hsb != null) {
                hsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }

            scroll.setCorner(JScrollPane.LOWER_RIGHT_CORNER, PanelUtil.createCorner());
            scroll.setCorner(JScrollPane.UPPER_RIGHT_CORNER, PanelUtil.createCorner());
            scroll.setCorner(JScrollPane.LOWER_LEFT_CORNER, PanelUtil.createCorner());
            scroll.setCorner(JScrollPane.UPPER_LEFT_CORNER, PanelUtil.createCorner());

            repaint();
        } catch (Exception ignored) {
        }
    }

    private void restyleExistingText() {
        SwingUtilities.invokeLater(() -> {
            int length = document.getLength();
            int pos = 0;

            while (pos < length) {
                Element element = document.getCharacterElement(pos);
                AttributeSet attrs = element.getAttributes();

                Object kind = attrs.getAttribute(ATTR_KIND);

                SimpleAttributeSet newAttrs = new SimpleAttributeSet(attrs);

                if (KIND_TIMESTAMP.equals(kind)) {
                    StyleConstants.setForeground(
                        newAttrs,
                        Theme.CONTENT_HIGHLIGHT_TEXT_COLOR
                    );
                } else if (KIND_MESSAGE.equals(kind)) {
                    StyleConstants.setForeground(
                        newAttrs,
                        Theme.CONTENT_TEXT_COLOR
                    );
                }

                int start = element.getStartOffset();
                int end = element.getEndOffset();

                document.setCharacterAttributes(
                    start,
                    end - start,
                    newAttrs,
                    true
                );

                pos = end;
            }
        });
    }

    public void init() {

    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            Entry entry = new Entry(dateFormat.format(new Date()), message);
            entries.add(entry);
            if (matches(entry)) {
                append(entry);
                logPane.setCaretPosition(document.getLength());
            }
            messageListeners.forEach(listener -> listener.accept(message));
        });
    }

    private boolean matches(Entry entry) {
        String text = filter.getText().strip();
        return text.isEmpty()
            || entry.message().toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    private void append(Entry entry) {
        try {
            SimpleAttributeSet tsAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(tsAttrs, Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
            tsAttrs.addAttribute(ATTR_KIND, KIND_TIMESTAMP);
            document.insertString(document.getLength(), entry.timestamp() + ": ", tsAttrs);

            SimpleAttributeSet msgAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(msgAttrs, Theme.CONTENT_TEXT_COLOR);
            msgAttrs.addAttribute(ATTR_KIND, KIND_MESSAGE);
            document.insertString(document.getLength(), entry.message() + "\n", msgAttrs);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    // Show again the entries the filter lets through.
    private void render() {
        try {
            document.remove(0, document.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        entries.stream().filter(this::matches).forEach(this::append);
        logPane.setCaretPosition(document.getLength());
    }

    private void copyShown() {
        String text = logPane.getSelectedText();
        if (text == null || text.isEmpty()) {
            text = logPane.getText();
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private void clear() {
        entries.clear();
        render();
    }

    /**
     * Be told each message as it is logged, on the event thread.
     */
    public void addMessageListener(Consumer<String> listener) {
        messageListeners.add(listener);
    }
}
