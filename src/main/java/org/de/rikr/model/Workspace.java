package org.de.rikr.model;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Every class loaded into the project, grouped by the JAR it came from. Loose class files share one group that
 * has no source file.
 * <p>
 * Classes are renamed in place, so callers that change class names must call {@link #classesChanged} to keep
 * lookups by name correct.
 */
public class Workspace {
    public static final String LOOSE_CLASSES = "Classes";

    private final Map<String, Jar> jars = new HashMap<>();

    public synchronized boolean contains(String jar) {
        return jars.containsKey(jar);
    }

    /**
     * @throws IllegalArgumentException if a JAR with this name is already loaded
     */
    public synchronized void addJar(String jar, List<ClassNode> classes, File source) {
        if (jars.containsKey(jar)) {
            throw new IllegalArgumentException("A JAR named " + jar + " is already loaded");
        }
        jars.put(jar, new Jar(new ArrayList<>(classes), source == null ? null : source.getAbsoluteFile()));
    }

    public synchronized void addLooseClass(ClassNode classNode) {
        Jar group = jars.computeIfAbsent(LOOSE_CLASSES, name -> new Jar(new ArrayList<>(), null));
        group.classes.add(classNode);
        group.byName = null;
    }

    /**
     * A live, read-only view of the JAR's classes, or null when the JAR is not loaded.
     */
    public synchronized List<ClassNode> classes(String jar) {
        Jar entry = jars.get(jar);
        return entry == null ? null : Collections.unmodifiableList(entry.classes);
    }

    /**
     * A copy of the JAR's classes that is safe to iterate on another thread, or null when the JAR is not loaded.
     */
    public synchronized List<ClassNode> classesSnapshot(String jar) {
        Jar entry = jars.get(jar);
        return entry == null ? null : List.copyOf(entry.classes);
    }

    public synchronized Map<String, List<ClassNode>> classesByJar() {
        Map<String, List<ClassNode>> copy = new HashMap<>();
        jars.forEach((name, entry) -> copy.put(name, List.copyOf(entry.classes)));
        return Map.copyOf(copy);
    }

    public synchronized Optional<ClassNode> findClass(ClassRef ref) {
        Jar entry = jars.get(ref.jar());
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.byName == null) {
            entry.byName = new HashMap<>();
            for (ClassNode classNode : entry.classes) {
                entry.byName.putIfAbsent(classNode.name, classNode);
            }
        }
        return Optional.ofNullable(entry.byName.get(ref.className()));
    }

    /**
     * Call after classes of the JAR were renamed in place.
     */
    public synchronized void classesChanged(String jar) {
        Jar entry = jars.get(jar);
        if (entry != null) {
            entry.byName = null;
        }
    }

    public synchronized boolean removeJar(String jar) {
        return jars.remove(jar) != null;
    }

    public synchronized boolean removeClass(String jar, ClassNode classNode) {
        Jar entry = jars.get(jar);
        if (entry == null || !entry.classes.remove(classNode)) {
            return false;
        }
        entry.byName = null;
        return true;
    }

    /**
     * The JAR that holds this class node.
     */
    public synchronized Optional<String> jarOf(ClassNode classNode) {
        for (Map.Entry<String, Jar> entry : jars.entrySet()) {
            for (ClassNode candidate : entry.getValue().classes) {
                if (candidate == classNode) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The file the JAR was loaded from, or null for loose classes and unknown JARs.
     */
    public synchronized File sourceFile(String jar) {
        Jar entry = jars.get(jar);
        return entry == null ? null : entry.source;
    }

    /**
     * Every JAR that was loaded from a file and can be reopened, sorted by name.
     */
    public synchronized Map<String, File> sourceFiles() {
        Map<String, File> files = new TreeMap<>();
        jars.forEach((name, entry) -> {
            if (entry.source != null) {
                files.put(name, entry.source);
            }
        });
        return Collections.unmodifiableMap(files);
    }

    private static final class Jar {
        private final List<ClassNode> classes;
        private final File source;
        private Map<String, ClassNode> byName;

        private Jar(List<ClassNode> classes, File source) {
            this.classes = classes;
            this.source = source;
        }
    }
}
