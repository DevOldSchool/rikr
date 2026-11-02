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
        if (!confirmClose()) {
            return;
        }
        JFileChooser chooser = createChooser("Open Rikr Project");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        openWithoutAsking(chooser.getSelectedFile());
    }

    /**
     * Open a project file without choosing it, for example from the recent projects list.
     */
    public void open(File project) {
        if (confirmClose()) {
            openWithoutAsking(project);
        }
    }

    private void openWithoutAsking(File project) {
        controller.openProject(project, exception -> showFailure("Open Project", exception));
    }

    /**
     * Before the open project is closed, offer to save the work that only its file keeps.
     *
     * @return false if the person cancelled, or chose to save and the project was not saved
     */
    public boolean confirmClose() {
        int changes = controller.unsavedChanges();
        if (changes == 0) {
            return true;
        }
        boolean hasFile = controller.getCurrentProjectFile() != null;
        Object[] options = {hasFile ? "Save" : "Save…", "Don’t Save", "Cancel"};
        int choice = JOptionPane.showOptionDialog(frame,
            "<html><b>Save this project before closing it?</b><br><br>"
                + changes + (changes == 1 ? " change" : " changes") + (hasFile ? " since it was last saved" : "")
                + " will be lost."
                + "<br>Renames, class mappings and bookmarks are kept only in the project file.</html>",
            "Unsaved Changes", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return switch (choice) {
            case 0 -> saveProject();
            case 1 -> true;
            default -> false;
        };
    }

    /**
     * @return whether the project was saved
     */
    public boolean saveProject() {
        File current = controller.getCurrentProjectFile();
        if (current == null) {
            return saveProjectAs();
        }
        return save(current);
    }

    /**
     * @return whether the project was saved
     */
    public boolean saveProjectAs() {
        JFileChooser chooser = createChooser("Save Rikr Project");
        chooser.setSelectedFile(new File("project.rikr"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File destination = chooser.getSelectedFile();
        if (!destination.getName().toLowerCase().endsWith(".rikr")) {
            destination = new File(destination.getParentFile(), destination.getName() + ".rikr");
        }
        return save(destination);
    }

    private boolean save(File destination) {
        try {
            controller.saveProject(destination);
            return true;
        } catch (Exception exception) {
            showFailure("Save Project", exception);
            return false;
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
