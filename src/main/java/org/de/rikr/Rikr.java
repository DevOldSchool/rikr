package org.de.rikr;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.project.Bookmarks;
import org.de.rikr.project.JarOperations;
import org.de.rikr.project.MappingOperations;
import org.de.rikr.project.Project;
import org.de.rikr.project.ProjectEvent;
import org.de.rikr.project.RenameOperations;
import org.de.rikr.resources.Fonts;
import org.de.rikr.resources.Images;
import org.de.rikr.service.DecompilerCacheService;
import org.de.rikr.service.ProjectBookmark;
import org.de.rikr.service.ProjectSession;
import org.de.rikr.service.ProjectSessionService;
import org.de.rikr.service.ReferenceIndexService;
import org.de.rikr.service.RenameUndoResult;
import org.de.rikr.service.SearchQuery;
import org.de.rikr.service.SearchResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.ui.ClassNodeImages;
import org.de.rikr.ui.ClassViewer;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class Rikr {
    private final ClassViewer userInterface;
    private final ProjectSessionService projectSessionService = new ProjectSessionService();
    private Project project;
    private Runnable unsubscribe = () -> {
    };
    private File currentProjectFile;

    public Rikr() {
        userInterface = new ClassViewer(this);
    }

    public void start() {
        userInterface.init();
        useProject(new Project(userInterface.getLogPanel()));
    }

    public ClassViewer getUserInterface() {
        return userInterface;
    }

    public void log(String message) {
        userInterface.getLogPanel().log(message);
    }

    public JarOperations jars() {
        return project.jars();
    }

    public RenameOperations renames() {
        return project.renames();
    }

    public MappingOperations mappings() {
        return project.mappings();
    }

    public Bookmarks bookmarks() {
        return project.bookmarks();
    }

    public ReferenceIndexService references() {
        return project.references();
    }

    public DecompilerCacheService decompiler() {
        return project.decompiler();
    }

    public List<SearchResult> search(SearchQuery query) {
        return project.search(query);
    }

    public void undoLastRename() {
        Optional<RenameUndoResult> undo = project.renames().undoLast();
        if (undo.isEmpty()) {
            log("No rename to undo.");
            return;
        }
        RenameUndoResult value = undo.get();
        userInterface.updateTree(project.jars().classesByJar());
        userInterface.displayBytecode(value.affectedClass());
        log("Undid rename " + value.operation().newName() + " -> " + value.operation().oldName() + ".");
    }

    public boolean toggleBookmark(ProjectBookmark bookmark) {
        boolean added = project.bookmarks().toggle(bookmark);
        log((added ? "Bookmarked " : "Removed bookmark ") + bookmark);
        return added;
    }

    public boolean openBookmark(ProjectBookmark bookmark) {
        return project.bookmarks().findClass(bookmark)
            .map(owner -> userInterface.openBookmark(bookmark, owner))
            .orElse(false);
    }

    public void saveProject(File destination) throws IOException {
        projectSessionService.save(destination, project.toSession(userInterface.captureProjectLayout()));
        currentProjectFile = destination.getAbsoluteFile();
        userInterface.updateProjectTitle(currentProjectFile);
        log("Saved project to " + currentProjectFile.getAbsolutePath());
    }

    public void openProject(File source) throws IOException {
        ProjectSession session = projectSessionService.load(source);
        Project opened = Project.open(userInterface.getLogPanel(), session);
        Project previous = project;
        useProject(opened);
        if (previous != null) {
            previous.close();
        }
        currentProjectFile = source.getAbsoluteFile();
        userInterface.updateTree(project.jars().classesByJar());
        userInterface.applyProjectLayout(session.layout());
        userInterface.updateProjectTitle(currentProjectFile);
        refreshProjectState();
        log("Opened project " + currentProjectFile.getAbsolutePath());
    }

    public File getCurrentProjectFile() {
        return currentProjectFile;
    }

    public void shutdown() {
        if (project != null) {
            project.close();
        }
    }

    private void useProject(Project next) {
        unsubscribe.run();
        project = next;
        unsubscribe = next.events().subscribe(this::onProjectEvent);
    }

    private void onProjectEvent(ProjectEvent event) {
        switch (event) {
            case ProjectEvent.JarsLoaded loaded -> userInterface.updateTree(project.jars().classesByJar());
            case ProjectEvent.JarRemoved removed -> refreshProjectState();
            case ProjectEvent.ClassRemoved removed -> {
            }
            case ProjectEvent.Renamed renamed -> refreshProjectState();
            case ProjectEvent.JarsSaved saved -> refreshProjectState();
            case ProjectEvent.BookmarksChanged changed -> userInterface.refreshBookmarks();
        }
    }

    private void refreshProjectState() {
        userInterface.updateDirtyJars(project.renames().dirtyJars());
        userInterface.refreshBookmarks();
    }

    public static void main(String[] args) {
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
            System.setProperty("apple.awt.application.name", "Rikr");
        }

        Images.loadImages();
        ClassNodeImages.loadImages();
        Fonts.loadFonts();

        SwingUtilities.invokeLater(() -> {
            SettingsManager.applyThemeStartup();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}
