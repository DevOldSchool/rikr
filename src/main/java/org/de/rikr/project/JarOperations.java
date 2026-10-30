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
        List<String> loaded = new ArrayList<>();
        for (File file : jarFiles) {
            // JARs are identified by file name, so a second JAR with the same name would be ambiguous.
            if (workspace.contains(file.getName())) {
                File existing = workspace.sourceFile(file.getName());
                logger.log("Skipped " + file.getAbsolutePath() + ": a JAR named " + file.getName()
                    + " is already loaded" + (existing == null ? "" : " from " + existing.getAbsolutePath())
                    + ". Remove it first.");
                continue;
            }
            try {
                LoadedJar jar = loader.readJar(file, stripAnnotations);
                workspace.addJar(jar.jarName(), jar.classes(), jar.originalFile());
                loaded.add(jar.jarName());
                logger.log("Loaded " + jar.classes().size() + " classes from " + jar.jarName());
            } catch (IOException e) {
                logger.log("Error processing jar file: " + file.getName() + ": " + e.getMessage());
            }
        }
        if (!loaded.isEmpty()) {
            events.publish(new ProjectEvent.JarsLoaded(loaded));
        }
        return loaded;
    }

    public void loadClassFiles(File[] classFiles) {
        boolean added = false;
        for (File file : classFiles) {
            try {
                workspace.addLooseClass(loader.readClass(file, stripAnnotations));
                added = true;
            } catch (IOException e) {
                logger.log("Error processing class file: " + file.getName() + ": " + e.getMessage());
            }
        }
        if (added) {
            events.publish(new ProjectEvent.JarsLoaded(List.of(Workspace.LOOSE_CLASSES)));
        }
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
}
