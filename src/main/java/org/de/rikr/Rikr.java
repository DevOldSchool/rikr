package org.de.rikr;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.decompiler.DecompilerCacheService;
import org.de.rikr.jar.LoadedJar;
import org.de.rikr.project.Bookmarks;
import org.de.rikr.project.JarOperations;
import org.de.rikr.project.MappingOperations;
import org.de.rikr.project.Project;
import org.de.rikr.project.ProjectEvent;
import org.de.rikr.project.ProjectSession;
import org.de.rikr.project.ProjectSessionService;
import org.de.rikr.project.RenameOperations;
import org.de.rikr.reference.ReferenceIndexService;
import org.de.rikr.rename.RenameUndoResult;
import org.de.rikr.resources.Fonts;
import org.de.rikr.resources.Images;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.task.TaskRunner;
import org.de.rikr.ui.ClassNodeImages;
import org.de.rikr.ui.ClassViewer;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class Rikr {
    private final ClassViewer userInterface;
    private final ProjectSessionService projectSessionService = new ProjectSessionService();
    private final TaskRunner tasks;
    private Project project;
    private Runnable unsubscribe = () -> {
    };
    private File currentProjectFile;

    public Rikr() {
        userInterface = new ClassViewer(this);
        tasks = new TaskRunner(userInterface.getLogPanel());
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

    /**
     * Load JAR and class files in the background, then add them to the project that is open now. If another
     * project is opened first, the files are dropped.
     */
    public void loadFiles(List<File> files) {
        List<File> jarFiles = files.stream().filter(file -> file.getName().endsWith(".jar")).toList();
        List<File> classFiles = files.stream().filter(file -> file.getName().endsWith(".class")).toList();
        if (jarFiles.isEmpty() && classFiles.isEmpty()) {
            return;
        }
        Project target = project;
        JarOperations jars = target.jars();
        log("Loading " + files.stream().map(File::getName).toList() + "...");
        tasks.run(
            "Loading files",
            () -> new LoadedFiles(jars.readJars(jarFiles), jars.readClassFiles(classFiles)),
            loaded -> {
                if (project != target) {
                    log("Did not add " + files.size() + " files because another project was opened.");
                    return;
                }
                jars.addJars(loaded.jars());
                jars.addClassFiles(loaded.classes());
            }
        );
    }

    /**
     * Open a project in the background and switch to it when it is ready.
     *
     * @param onFailure called on the event thread if the project cannot be opened
     */
    public void openProject(File source, Consumer<Exception> onFailure) {
        log("Opening project " + source.getAbsolutePath() + "...");
        tasks.run(
            "Opening project",
            () -> {
                ProjectSession session = projectSessionService.load(source);
                return new OpenedProject(Project.open(userInterface.getLogPanel(), session), session);
            },
            opened -> showProject(opened, source),
            onFailure
        );
    }

    private void showProject(OpenedProject opened, File source) {
        Project previous = project;
        useProject(opened.project());
        if (previous != null) {
            previous.close();
        }
        currentProjectFile = source.getAbsoluteFile();
        // The code view shows a class of the closed project.
        userInterface.clearContent();
        userInterface.updateTree(project.jars().classesByJar());
        userInterface.applyProjectLayout(opened.session().layout());
        userInterface.updateProjectTitle(currentProjectFile);
        refreshProjectState();
        log("Opened project " + currentProjectFile.getAbsolutePath());
    }

    public File getCurrentProjectFile() {
        return currentProjectFile;
    }

    public void shutdown() {
        tasks.close();
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
            case ProjectEvent.Renamed renamed -> {
                userInterface.refreshTreeLabels();
                refreshProjectState();
            }
            case ProjectEvent.JarsSaved saved -> refreshProjectState();
            case ProjectEvent.BookmarksChanged changed -> userInterface.refreshBookmarks();
        }
    }

    private void refreshProjectState() {
        userInterface.updateDirtyJars(project.renames().dirtyJars());
        userInterface.refreshBookmarks();
    }

    private record LoadedFiles(List<LoadedJar> jars, List<ClassNode> classes) {
    }

    private record OpenedProject(Project project, ProjectSession session) {
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
