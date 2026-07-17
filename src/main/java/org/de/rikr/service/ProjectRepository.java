package org.de.rikr.service;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectRepository {
    private final Map<String, List<ClassNode>> jarClassesMap = new HashMap<>();
    private final Map<String, File> originalJarPaths = new HashMap<>();

    public synchronized void putJarClasses(String jarName, List<ClassNode> classes, File originalJarFile) {
        jarClassesMap.put(jarName, new ArrayList<>(classes));
        if (originalJarFile != null) {
            originalJarPaths.put(jarName, originalJarFile.getAbsoluteFile());
        }
    }

    public synchronized void addClass(String groupName, ClassNode classNode) {
        jarClassesMap.computeIfAbsent(groupName, k -> new ArrayList<>()).add(classNode);
    }

    public synchronized Map<String, List<ClassNode>> getJarClassesView() {
        Map<String, List<ClassNode>> copy = new HashMap<>();
        for (Map.Entry<String, List<ClassNode>> entry : jarClassesMap.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Map.copyOf(copy);
    }

    public synchronized List<ClassNode> getClasses(String jarName) {
        return jarClassesMap.get(jarName);
    }

    public synchronized boolean removeJar(String jarName) {
        originalJarPaths.remove(jarName);
        return jarClassesMap.remove(jarName) != null;
    }

    public synchronized boolean removeClass(String jarName, ClassNode classNode) {
        List<ClassNode> list = jarClassesMap.get(jarName);
        if (list == null) {
            return false;
        }

        return list.remove(classNode);
    }

    public synchronized File getOriginalJarFile(String jarName) {
        return originalJarPaths.get(jarName);
    }
}
