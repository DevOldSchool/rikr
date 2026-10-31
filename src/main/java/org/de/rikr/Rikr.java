package org.de.rikr;

import com.formdev.flatlaf.util.SystemInfo;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.decompiler.DecompilerCacheService;
import org.de.rikr.jar.JarExportService;
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
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameStep;
import org.de.rikr.rename.RenameType;
import org.de.rikr.rename.RenameUndoResult;
import org.de.rikr.resources.Fonts;
import org.de.rikr.resources.Images;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.task.TaskRunner;
import org.de.rikr.ui.ClassNodeImages;
import org.de.rikr.ui.ClassViewer;
import org.de.rikr.ui.RecentProjects;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
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
        tasks.setBusyListener(userInterface::setBusy);
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

    /**
     * Run slow work that doesn't touch the project in the background, and act on its result on the event thread.
     *
     * @param onFailure called on the event thread if the work or {@code onSuccess} throws
     */
    public <T> void runInBackground(String name, Callable<T> work, Consumer<T> onSuccess,
        Consumer<Exception> onFailure) {
        tasks.run(name, work, onSuccess, onFailure);
    }

    public void undoLastRename() {
        Optional<RenameUndoResult> undo = project.renames().undoLast();
        if (undo.isEmpty()) {
            log("No rename to undo.");
            return;
        }
        RenameUndoResult value = undo.get();
        if (value.affectedClass() != null) {
            userInterface.displayBytecode(value.affectedClass());
        } else {
            userInterface.refreshCode();
        }
        switch (value.step()) {
            case RenameOperation operation ->
                log("Undid rename " + operation.newName() + " -> " + operation.oldName() + ".");
            case RenameBatch batch -> log("Undid the import of " + batch.source() + ".");
        }
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
        RecentProjects.add(currentProjectFile);
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
        List<File> otherFiles = files.stream()
            .filter(file -> !jarFiles.contains(file) && !classFiles.contains(file))
            .toList();
        if (jarFiles.isEmpty() && classFiles.isEmpty()) {
            userInterface.showUnreadableFiles(otherFiles);
            return;
        }
        Project target = project;
        JarOperations jars = target.jars();
        log("Loading " + files.stream().map(File::getName).toList() + "...");
        tasks.run(
            "Loading files",
            () -> {
                List<File> unreadable = new ArrayList<>(otherFiles);
                return new LoadedFiles(jars.readJars(jarFiles, unreadable::add),
                    jars.readClassFiles(classFiles, unreadable::add), unreadable);
            },
            loaded -> {
                userInterface.showUnreadableFiles(loaded.unreadable());
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
     * Export a JAR with its renamed classes to a new file. The classes are converted to bytes now and written in
     * the background.
     *
     * @param onFailure called on the event thread if the JAR cannot be written
     */
    public void exportJar(String jar, File destination, Consumer<Exception> onFailure) {
        Project target = project;
        JarOperations.JarSnapshot snapshot;
        try {
            snapshot = target.jars().snapshot(jar);
        } catch (IOException exception) {
            onFailure.accept(exception);
            return;
        }
        long changeCount = target.renames().changeCount();
        log("Saving " + jar + " to " + destination.getAbsolutePath() + "...");
        tasks.run(
            "Saving " + jar,
            () -> target.jars().write(snapshot, destination),
            result -> {
                logExport(result, destination);
                target.renames().markExported(jar, changeCount);
            },
            onFailure
        );
    }

    /**
     * Replace the JAR file a JAR was loaded from, keeping a backup next to it. Its renames are then part of the
     * file, so they leave the rename history.
     *
     * @param onFailure called on the event thread if the JAR cannot be written
     */
    public void overwriteOriginalJar(String jar, Consumer<Exception> onFailure) {
        Project target = project;
        JarOperations.JarSnapshot snapshot;
        try {
            snapshot = target.jars().snapshot(jar);
        } catch (IOException exception) {
            onFailure.accept(exception);
            return;
        }
        long changeCount = target.renames().changeCount();
        List<RenameStep> written = target.renames().history().stream()
            .filter(operation -> operation.jarName().equals(jar))
            .toList();
        log("Overwriting " + jar + "...");
        tasks.run(
            "Overwriting " + jar,
            () -> target.jars().overwriteOriginal(snapshot),
            result -> {
                logExport(result, snapshot.source());
                log("Kept the previous file as " + snapshot.source().getName() + ".bak");
                target.renames().commitOverwrittenJar(jar, written, changeCount);
            },
            onFailure
        );
    }

    private void logExport(JarExportService.ExportResult result, File destination) {
        log("Saved " + result.classes() + " classes and " + result.resources() + " resources to "
            + destination.getAbsolutePath());
        if (result.removedSignatureFiles() > 0) {
            log("Left out " + result.removedSignatureFiles()
                + " signature files: the classes changed, so the signature no longer matches.");
        }
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
        RecentProjects.add(currentProjectFile);
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
                // A class rename can move a class to another package, so the tree is rebuilt; it keeps its place.
                boolean classesRenamed = switch (renamed.step()) {
                    case RenameOperation operation -> operation.type() == RenameType.CLASS;
                    case RenameBatch batch -> !batch.classes().isEmpty();
                };
                if (classesRenamed) {
                    userInterface.updateTree(project.jars().classesByJar());
                }
                userInterface.refreshTreeLabels();
                userInterface.refreshControlFlow();
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

    private record LoadedFiles(List<LoadedJar> jars, List<ClassNode> classes, List<File> unreadable) {
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
            SettingsManager.watchSystemAppearance();
            Rikr rikr = new Rikr();
            rikr.start();
        });
    }
}
