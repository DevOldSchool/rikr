package org.de.rikr.ui;

import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the code view shows when no class is open: how to start when nothing is loaded, and how to open a class
 * when JARs are. JAR and class files can be dropped on it.
 */
public class EmptyStatePanel extends JPanel {
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    private final JPanel recent = new JPanel();
    private final JLabel recentTitle = new JLabel("Recent projects");
    private final Consumer<File> openProject;

    public EmptyStatePanel(Runnable openFiles, Runnable chooseProject, Consumer<File> openProject,
                           Consumer<List<File>> dropFiles) {
        super(new GridBagLayout());
        this.openProject = openProject;
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 8));
        detail.setFont(detail.getFont().deriveFont(detail.getFont().getSize2D() + 1));
        for (JComponent component : List.of(title, detail, actions, recentTitle, recent)) {
            component.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        JButton open = new JButton("Open JAR or Class...");
        open.addActionListener(event -> openFiles.run());
        JButton project = new JButton("Open Project...");
        project.addActionListener(event -> chooseProject.run());
        actions.setOpaque(false);
        actions.add(open);
        actions.add(project);
        recentTitle.setFont(recentTitle.getFont().deriveFont(Font.BOLD));
        recent.setOpaque(false);
        recent.setLayout(new BoxLayout(recent, BoxLayout.Y_AXIS));

        content.add(title);
        content.add(Box.createVerticalStrut(10));
        content.add(detail);
        content.add(Box.createVerticalStrut(24));
        content.add(actions);
        content.add(Box.createVerticalStrut(32));
        content.add(recentTitle);
        content.add(Box.createVerticalStrut(8));
        content.add(recent);
        add(content);

        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                    dropFiles.accept(files);
                    return true;
                } catch (Exception exception) {
                    return false;
                }
            }
        });
        applyTheme();
        SettingsManager.addListener(settings -> applyTheme());
    }

    /**
     * @param jarsLoaded whether to explain how to open a class rather than how to load a JAR
     */
    public void update(boolean jarsLoaded, List<File> recentProjects) {
        if (jarsLoaded) {
            title.setText("Choose a class to open");
            // Two short lines rather than one long one, which a narrow or scaled-up window would cut off.
            detail.setText("<html><div style='text-align:center'>Select one in the project tree,<br>"
                + "or press Cmd+K to go to a class or member by name.</div></html>");
        } else {
            title.setText("Open a JAR to start");
            detail.setText("Or drop .jar and .class files anywhere here.");
        }
        actions.setVisible(!jarsLoaded);
        recent.removeAll();
        for (File project : recentProjects) {
            JButton link = new JButton(project.getName());
            link.setToolTipText(project.getAbsolutePath());
            link.setBorderPainted(false);
            link.setContentAreaFilled(false);
            link.setForeground(Theme.SYNTAX_HIGHLIGHT_NUMBER_COLOR);
            link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            link.setAlignmentX(Component.CENTER_ALIGNMENT);
            link.addActionListener(event -> openProject.accept(project));
            recent.add(link);
        }
        boolean showRecent = !jarsLoaded && !recentProjects.isEmpty();
        recentTitle.setVisible(showRecent);
        recent.setVisible(showRecent);
        revalidate();
        repaint();
    }

    private void applyTheme() {
        setBackground(Theme.CONTENT_BACKGROUND_COLOR);
        title.setForeground(Theme.SYNTAX_HIGHLIGHT_DEFAULT_COLOR);
        detail.setForeground(Theme.CONTENT_TEXT_COLOR);
        recentTitle.setForeground(Theme.CONTENT_TEXT_COLOR);
    }
}
