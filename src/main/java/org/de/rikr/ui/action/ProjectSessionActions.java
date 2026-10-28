package org.de.rikr.ui.action;

import org.de.rikr.Rikr;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public class ProjectSessionActions {
    private final Rikr controller;
    private final JFrame frame;

    public ProjectSessionActions(Rikr controller, JFrame frame) {
        this.controller = controller;
        this.frame = frame;
    }

    public void openProject() {
        JFileChooser chooser = createChooser("Open Rikr Project");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            controller.openProject(chooser.getSelectedFile());
        } catch (Exception exception) {
            showFailure("Open Project", exception);
        }
    }

    public void saveProject() {
        File current = controller.getCurrentProjectFile();
        if (current == null) {
            saveProjectAs();
            return;
        }
        save(current);
    }

    public void saveProjectAs() {
        JFileChooser chooser = createChooser("Save Rikr Project");
        chooser.setSelectedFile(new File("project.rikr"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith(".rikr")) {
            destination = new File(destination.getParentFile(), destination.getName() + ".rikr");
        }
        save(destination);
    }

    private void save(File destination) {
        try {
            controller.saveProject(destination);
        } catch (Exception exception) {
            showFailure("Save Project", exception);
        }
    }

    private JFileChooser createChooser(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Rikr projects (*.rikr)", "rikr"));
        return chooser;
    }

    private void showFailure(String title, Exception exception) {
        controller.log(title + " failed: " + exception.getMessage());
        JOptionPane.showMessageDialog(frame, exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
