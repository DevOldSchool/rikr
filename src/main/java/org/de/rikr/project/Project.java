package org.de.rikr.project;

import org.de.rikr.Logger;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.decompiler.DecompilerCacheService;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.model.Workspace;
import org.de.rikr.reference.ReferenceIndexService;
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameStep;
import org.de.rikr.rename.RenameType;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.search.SearchService;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything loaded into one project, grouped by feature. Changes are announced on {@link #events()}; the
 * project subscribes first, so caches, indexes, mappings and bookmarks are up to date before other
 * listeners such as the UI run.
 */
public class Project implements AutoCloseable {
    private final Logger logger;
    private final Workspace workspace = new Workspace();
    private final EventBus events = new EventBus();
    private final JarOperations jars;
    private final RenameOperations renames;
    private final MappingOperations mappings;
    private final Bookmarks bookmarks;
    private final ReferenceIndexService references = new ReferenceIndexService();
    private final DecompilerCacheService decompiler;
    private final SearchService searchService = new SearchService();

    public Project(Logger logger) {
        this.logger = logger;
        this.jars = new JarOperations(logger, workspace, events);
        this.renames = new RenameOperations(workspace, events);
        this.mappings = new MappingOperations(workspace);
        this.bookmarks = new Bookmarks(workspace, events);
        this.decompiler = new DecompilerCacheService(logger);
        events.subscribe(this::onEvent);
    }

    /**
     * Load a saved project: its JARs, then its renames, mappings and bookmarks.
     *
     * @throws IOException if a JAR is missing or the saved state does not match the JARs
     */
    public static Project open(Logger logger, ProjectSession session) throws IOException {
        File[] jarFiles = session.jarPaths().stream().map(File::new).toArray(File[]::new);
        Set<String> jarNames = new HashSet<>();
        for (File jar : jarFiles) {
            if (!jar.isFile()) {
                throw new IOException("Project JAR does not exist: " + jar.getAbsolutePath());
            }
            if (!jarNames.add(jar.getName())) {
                throw new IOException("Project contains duplicate JAR names: " + jar.getName());
            }
        }

        Project project = new Project(logger);
        try {
            project.jars.loadJars(jarFiles);
            for (File jar : jarFiles) {
                if (project.workspace.sourceFile(jar.getName()) == null) {
                    throw new IOException("Unable to load project JAR: " + jar.getAbsolutePath());
                }
            }
            for (String jar : project.renames.replay(session.renames())) {
                project.refreshJar(jar);
            }
            project.mappings.restore(session.mappings());
            project.bookmarks.replaceAll(session.bookmarks());
            return project;
        } catch (IOException exception) {
            project.close();
            throw exception;
        } catch (RuntimeException exception) {
            project.close();
            throw new IOException("Unable to restore project: " + exception.getMessage(), exception);
        }
    }

    /**
     * The state to save. Loose class files have no file to reopen, so state that refers to them is left out.
     */
    public ProjectSession toSession(ProjectLayout layout) {
        Map<String, File> savedJars = workspace.sourceFiles();
        List<String> jarPaths = savedJars.values().stream()
            .map(File::getAbsolutePath)
            .sorted()
            .toList();
        List<ClassMappingDecision> allMappings = mappings.allAccepted();
        List<ClassMappingDecision> savedMappings = allMappings.stream()
            .filter(mapping -> savedJars.containsKey(mapping.sourceJar()) && savedJars.containsKey(mapping.targetJar()))
            .toList();
        List<RenameStep> allRenames = renames.history();
        List<RenameStep> savedRenames = allRenames.stream()
            .filter(operation -> savedJars.containsKey(operation.jarName()))
            .toList();
        List<ProjectBookmark> allBookmarks = bookmarks.all();
        List<ProjectBookmark> savedBookmarks = allBookmarks.stream()
            .filter(bookmark -> savedJars.containsKey(bookmark.jarName()))
            .toList();
        int skipped = allMappings.size() - savedMappings.size()
            + allRenames.size() - savedRenames.size()
            + allBookmarks.size() - savedBookmarks.size();
        if (skipped > 0) {
            logger.log("Project does not include " + skipped
                + " renames, mappings or bookmarks of loose class files; only JARs can be reopened.");
        }
        return new ProjectSession(jarPaths, savedMappings, savedRenames, savedBookmarks, layout);
    }

    public EventBus events() {
        return events;
    }

    public JarOperations jars() {
        return jars;
    }

    public RenameOperations renames() {
        return renames;
    }

    public MappingOperations mappings() {
        return mappings;
    }

    public Bookmarks bookmarks() {
        return bookmarks;
    }

    public ReferenceIndexService references() {
        return references;
    }

    public DecompilerCacheService decompiler() {
        return decompiler;
    }

    /**
     * Warm the decompiler up on these JARs' classes, so the first class opened decompiles sooner. Front ends call it
     * after loading JARs; the project doesn't, so tests and scripts don't start a decompiler for nothing.
     */
    public void warmUpDecompiler(Collection<String> jars) {
        decompiler.warmUp(jars.stream()
            .map(workspace::classesSnapshot)
            .filter(Objects::nonNull)
            .flatMap(List::stream)
            .toList());
    }

    public List<SearchResult> search(SearchQuery query) {
        return searchService.search(workspace.classesByJar(), query);
    }

    @Override
    public void close() {
        decompiler.close();
    }

    private void onEvent(ProjectEvent event) {
        switch (event) {
            case ProjectEvent.JarsLoaded loaded -> {
                for (String jar : loaded.jars()) {
                    references.indexJar(jar, workspace.classesSnapshot(jar));
                }
            }
            case ProjectEvent.JarRemoved removed -> {
                decompiler.invalidate(removed.classes());
                references.removeJar(removed.jar());
                mappings.removeJar(removed.jar());
                renames.removeJar(removed.jar());
                bookmarks.service().removeJar(removed.jar());
            }
            case ProjectEvent.ClassRemoved removed -> {
                mappings.removeClass(removed.jar(), removed.classNode().name);
                reindex(removed.jar());
            }
            case ProjectEvent.Renamed renamed -> {
                refreshJar(renamed.step().jarName());
                switch (renamed.step()) {
                    case RenameOperation operation -> {
                        if (operation.type() == RenameType.CLASS) {
                            String from = renamed.undo() ? operation.newName() : operation.oldName();
                            String to = renamed.undo() ? operation.oldName() : operation.newName();
                            mappings.renameClass(operation.jarName(), from, to);
                        }
                        bookmarks.service().applyRename(operation, renamed.undo(), renamed.declarationOwners());
                    }
                    case RenameBatch batch -> {
                        RenameBatch applied = renamed.undo() ? batch.inverse() : batch;
                        mappings.renameClasses(applied.jarName(), applied.classes());
                        bookmarks.service().applyBatch(applied);
                    }
                }
            }
            case ProjectEvent.JarsSaved saved -> {
            }
            case ProjectEvent.BookmarksChanged changed -> {
            }
        }
    }

    /**
     * Rebuild everything derived from a JAR's bytecode after its classes were renamed in place.
     */
    private void refreshJar(String jar) {
        workspace.classesChanged(jar);
        decompiler.invalidate(workspace.classesSnapshot(jar));
        reindex(jar);
    }

    private void reindex(String jar) {
        List<ClassNode> classes = workspace.classesSnapshot(jar);
        if (classes == null) {
            references.removeJar(jar);
        } else {
            references.indexJar(jar, classes);
        }
    }
}
