package org.de.rikr.ui;

import org.de.rikr.Logger;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogPanel extends JScrollPane implements Logger {
    private final JTextPane logPane;
    private final StyledDocument document;
    private final Style style;
    private final DateFormat dateFormat;
    private Date date;

    public LogPanel() {
        logPane = new JTextPane();
        logPane.setEditable(false);
        setViewportView(logPane);

        document = logPane.getStyledDocument();
        document.setCharacterAttributes(0, document.getLength(), new SimpleAttributeSet(), true);
        style = document.addStyle("log", null);
        dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    }

    public void init() {
        
    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                date = new Date();
                StyleConstants.setForeground(style, Color.GRAY);
                document.insertString(document.getLength(), dateFormat.format(date) + ": ", style);
                StyleConstants.setForeground(style, Color.LIGHT_GRAY);
                document.insertString(document.getLength(), message + "\n", style);
            } catch (BadLocationException e) {
                throw new RuntimeException(e);
            }
            logPane.setCaretPosition(document.getLength());
        });
    }
}

