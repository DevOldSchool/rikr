package org.de.rikr.project;

import org.de.rikr.Logger;
import org.de.rikr.jar.ClassLoadingService;
import org.de.rikr.jar.JarExportService;
import org.de.rikr.jar.LoadedJar;
import org.de.rikr.model.ClassRef;
import org.de.rikr.model.Workspace;
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
    private final JarExportService exporter;
    private volatile boolean stripAnnotations;

    JarOperations(Logger logger, Workspace workspace, EventBus events) {
        this.logger = logger;
        this.workspace = workspace;
        this.events = events;
        this.exporter = new JarExportService(logger);
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

    public void exportJar(String jar, File destination) throws IOException {
        List<ClassNode> classes = workspace.classesSnapshot(jar);
        if (classes == null) {
            throw new IOException("Unknown jar group: " + jar);
        }
        exporter.exportJar(destination, classes);
    }

    public boolean overwriteOriginalJar(String jar) throws IOException {
        return exporter.overwriteOriginalJar(workspace.sourceFile(jar), workspace.classesSnapshot(jar));
    }

    private void logAlreadyLoaded(File file) {
        File existing = workspace.sourceFile(file.getName());
        logger.log("Skipped " + file.getAbsolutePath() + ": a JAR named " + file.getName()
            + " is already loaded" + (existing == null ? "" : " from " + existing.getAbsolutePath())
            + ". Remove it first.");
    }
}
