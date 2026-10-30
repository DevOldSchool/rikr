package org.de.rikr.project;

import org.de.rikr.Logger;
import org.de.rikr.jar.ClassLoadingService;
import org.de.rikr.jar.JarExportService;
import org.de.rikr.jar.LoadedJar;
import org.de.rikr.model.ClassRef;
import org.de.rikr.model.Workspace;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.symbol.Symbol;
import org.de.rikr.symbol.SymbolResolver;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loading, removing and exporting the project's JARs and loose class files.
 */
public class JarOperations {
    private final Logger logger;
    private final Workspace workspace;
    private final EventBus events;
    private final ClassLoadingService loader = new ClassLoadingService();
    private final JarExportService exporter = new JarExportService();
    private volatile boolean stripAnnotations;

    JarOperations(Logger logger, Workspace workspace, EventBus events) {
        this.logger = logger;
        this.workspace = workspace;
        this.events = events;
    }

    /**
     * Load JARs, skipping any whose file name is already loaded.
     *
     * @return the names of the JARs that were loaded
     */
    public List<String> loadJars(File[] jarFiles) {
        return addJars(readJars(List.of(jarFiles)));
    }

    /**
     * Read JARs without adding them, so it is safe to call off the event thread. JARs that cannot be read, or
     * whose file name is already loaded, are logged and skipped.
     */
    public List<LoadedJar> readJars(List<File> jarFiles) {
        List<LoadedJar> read = new ArrayList<>();
        for (File file : jarFiles) {
            if (workspace.contains(file.getName())) {
                logAlreadyLoaded(file);
                continue;
            }
            try {
                read.add(loader.readJar(file, stripAnnotations));
            } catch (IOException e) {
                logger.log("Error processing jar file: " + file.getName() + ": " + e.getMessage());
            }
        }
        return read;
    }

    /**
     * Add JARs from {@link #readJars}, skipping any whose file name was loaded in the meantime.
     *
     * @return the names of the JARs that were added
     */
    public List<String> addJars(List<LoadedJar> jars) {
        List<String> added = new ArrayList<>();
        for (LoadedJar jar : jars) {
            // JARs are identified by file name, so a second JAR with the same name would be ambiguous.
            if (workspace.contains(jar.jarName())) {
                logAlreadyLoaded(jar.originalFile());
                continue;
            }
            workspace.addJar(jar.jarName(), jar.classes(), jar.originalFile());
            added.add(jar.jarName());
            logger.log("Loaded " + jar.classes().size() + " classes from " + jar.jarName());
        }
        if (!added.isEmpty()) {
            events.publish(new ProjectEvent.JarsLoaded(added));
        }
        return added;
    }

    /**
     * Read class files without adding them, so it is safe to call off the event thread. Files that cannot be
     * read are logged and skipped.
     */
    public List<ClassNode> readClassFiles(List<File> classFiles) {
        List<ClassNode> read = new ArrayList<>();
        for (File file : classFiles) {
            try {
                read.add(loader.readClass(file, stripAnnotations));
            } catch (IOException e) {
                logger.log("Error processing class file: " + file.getName() + ": " + e.getMessage());
            }
        }
        return read;
    }

    /**
     * Add classes from {@link #readClassFiles} to {@link Workspace#LOOSE_CLASSES}.
     */
    public void addClassFiles(List<ClassNode> classes) {
        if (classes.isEmpty()) {
            return;
        }
        classes.forEach(workspace::addLooseClass);
        events.publish(new ProjectEvent.JarsLoaded(List.of(Workspace.LOOSE_CLASSES)));
    }

    public void removeJar(String jar) {
        List<ClassNode> classes = workspace.classesSnapshot(jar);
        if (classes != null && workspace.removeJar(jar)) {
            events.publish(new ProjectEvent.JarRemoved(jar, classes));
        }
    }

    public void removeClass(String jar, ClassNode classNode) {
        if (workspace.removeClass(jar, classNode)) {
            events.publish(new ProjectEvent.ClassRemoved(jar, classNode));
        }
    }

    /**
     * A live, read-only view of the JAR's classes, or null when the JAR is not loaded.
     */
    public List<ClassNode> classes(String jar) {
        return workspace.classes(jar);
    }

    public Map<String, List<ClassNode>> classesByJar() {
        return workspace.classesByJar();
    }

    public Optional<ClassNode> findClass(ClassRef ref) {
        return workspace.findClass(ref);
    }

    /**
     * The class's name when it was loaded, before any rename.
     */
    public String originalName(ClassNode classNode) {
        return workspace.originalNames().className(classNode);
    }

    public Optional<String> jarOf(ClassNode classNode) {
        return workspace.jarOf(classNode);
    }

    /**
     * Where the symbol is declared in the JAR, if it is declared there at all.
     */
    public Optional<Declaration> resolve(String jar, Symbol symbol) {
        List<ClassNode> classes = workspace.classesSnapshot(jar);
        return classes == null ? Optional.empty() : SymbolResolver.resolve(classes, symbol);
    }

    public File sourceFile(String jar) {
        return workspace.sourceFile(jar);
    }

    public Map<String, File> sourceFiles() {
        return workspace.sourceFiles();
    }

    public void setStripAnnotations(boolean stripAnnotations) {
        this.stripAnnotations = stripAnnotations;
    }

    public void saveClass(ClassNode classNode, File destination) throws IOException {
        exporter.saveClassToFile(classNode, destination);
    }

    /**
     * A JAR's classes as class file bytes, ready to write on another thread. Take it on the event thread, where
     * renames happen, so no rename can change the classes while they are converted.
     */
    public record JarSnapshot(String jar, File source, Map<String, byte[]> classEntries) {
    }

    public JarSnapshot snapshot(String jar) throws IOException {
        List<ClassNode> classes = workspace.classesSnapshot(jar);
        if (classes == null) {
            throw new IOException("Unknown jar group: " + jar);
        }
        return new JarSnapshot(jar, workspace.sourceFile(jar), exporter.toEntries(classes));
    }

    /**
     * Write the snapshot to a new JAR, with the resources of the JAR it was loaded from. Safe off the event thread.
     */
    public JarExportService.ExportResult write(JarSnapshot snapshot, File destination) throws IOException {
        return exporter.writeJar(snapshot.source(), destination, snapshot.classEntries());
    }

    /**
     * Replace the JAR the snapshot was loaded from, keeping a backup. Safe off the event thread.
     */
    public JarExportService.ExportResult overwriteOriginal(JarSnapshot snapshot) throws IOException {
        if (snapshot.source() == null) {
            throw new IOException(snapshot.jar() + " was not loaded from a JAR file");
        }
        return exporter.overwriteOriginalJar(snapshot.source(), snapshot.classEntries());
    }

    private void logAlreadyLoaded(File file) {
        File existing = workspace.sourceFile(file.getName());
        logger.log("Skipped " + file.getAbsolutePath() + ": a JAR named " + file.getName()
            + " is already loaded" + (existing == null ? "" : " from " + existing.getAbsolutePath())
            + ". Remove it first.");
    }
}
