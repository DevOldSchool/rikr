package org.de.rikr.ui;

import org.de.rikr.Logger;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import javax.swing.text.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class LogPanel extends JScrollPane implements Logger {
    private final List<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();
    private static final String ATTR_KIND = "logKind";
    private static final String KIND_TIMESTAMP = "timestamp";
    private static final String KIND_MESSAGE = "message";

    private final JTextPane logPane;
    private final StyledDocument document;

    private final DateFormat dateFormat;

    public LogPanel() {
        setBorder(null);

        logPane = new JTextPane();
        logPane.setEditable(false);
        setViewportView(logPane);

        document = logPane.getStyledDocument();
        dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private void applyTheme() {
        try {
            logPane.setBackground(Theme.CONTENT_BACKGROUND_COLOR);
            logPane.setForeground(Theme.CONTENT_TEXT_COLOR);

            restyleExistingText();

            JScrollBar vsb = getVerticalScrollBar();
            if (vsb != null) {
                vsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }

            JScrollBar hsb = getHorizontalScrollBar();
            if (hsb != null) {
                hsb.setBackground(SettingsManager.getScrollBarTrackColor());
            }

            setCorner(JScrollPane.LOWER_RIGHT_CORNER, PanelUtil.createCorner());
            setCorner(JScrollPane.UPPER_RIGHT_CORNER, PanelUtil.createCorner());
            setCorner(JScrollPane.LOWER_LEFT_CORNER, PanelUtil.createCorner());
            setCorner(JScrollPane.UPPER_LEFT_CORNER, PanelUtil.createCorner());

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
            try {
                Date date = new Date();

                SimpleAttributeSet tsAttrs = new SimpleAttributeSet();
                StyleConstants.setForeground(tsAttrs, Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
                tsAttrs.addAttribute(ATTR_KIND, KIND_TIMESTAMP);

                document.insertString(
                    document.getLength(),
                    dateFormat.format(date) + ": ",
                    tsAttrs
                );

                SimpleAttributeSet msgAttrs = new SimpleAttributeSet();
                StyleConstants.setForeground(msgAttrs, Theme.CONTENT_TEXT_COLOR);
                msgAttrs.addAttribute(ATTR_KIND, KIND_MESSAGE);

                document.insertString(
                    document.getLength(),
                    message + "\n",
                    msgAttrs
                );
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }

            logPane.setCaretPosition(document.getLength());
            messageListeners.forEach(listener -> listener.accept(message));
        });
    }

    /**
     * Be told each message as it is logged, on the event thread.
     */
    public void addMessageListener(Consumer<String> listener) {
        messageListeners.add(listener);
    }
}
