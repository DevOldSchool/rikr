package org.de.rikr;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
import org.de.rikr.decompiler.JetBrainsDecompilerService;
import org.de.rikr.loader.ClassFileLoader;
import org.de.rikr.loader.JarLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassProcessor {
    private final Logger logger;
    private final Map<String, List<ClassNode>> jarClassesMap;
    private final ClassComparator classComparator;
    private final MethodComparator methodComparator;
    private boolean stripAnnotations;

    private final Map<String, Map<String, String>> decompiledByJar = new HashMap<>();
    private final Map<String, String> decompiledByClass = new HashMap<>();

    public ClassProcessor(Logger logger) {
        this.logger = logger;

        jarClassesMap = new HashMap<>();
        classComparator = new ClassComparator();
        methodComparator = new MethodComparator();
        stripAnnotations = false;
    }

    public void processJarFiles(File[] jarFiles) {
        for (File file : jarFiles) {
            try {
                Map<String, List<ClassNode>> singleJarClassesMap = readJarFile(file);
                jarClassesMap.put(file.getName(), singleJarClassesMap.get(file.getName()));
                logger.log("Loaded " + singleJarClassesMap.get(file.getName()).size() + " classes from " + file.getName());

                // Kick off background full-jar decompilation and cache results
                startBackgroundJarDecompile(file);
            } catch (IOException e) {
                logger.log("Error processing jar file: " + file.getName());
            }
        }
    }

    public void processClassFiles(File[] classFiles) {
        for (File file : classFiles) {
            try {
                ClassNode classNode = readClassFile(file);
                jarClassesMap.computeIfAbsent("Classes", k -> new ArrayList<>()).add(classNode);
            } catch (IOException e) {
                logger.log("Error processing class file: " + file.getName());
            }
        }
    }

    public List<ClassNode> findClassesExtending(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> extendedClasses = new ArrayList<>();

        for (ClassNode classNode : classes) {
            if (classNode.superName.equals(baseClassNode.name)) {
                extendedClasses.add(classNode);
            }
        }

        return extendedClasses;
    }

    public List<ClassNode> findClassesImplementing(List<ClassNode> classes, ClassNode baseClassNode) {
        List<ClassNode> implementingClasses = new ArrayList<>();

        for (ClassNode classNode : classes) {
            if (classNode.interfaces.contains(baseClassNode.name)) {
                implementingClasses.add(classNode);
            }
        }

        return implementingClasses;
    }

    public Map<String, List<ClassNode>> groupBySuperclass(List<ClassNode> classes) {
        Map<String, List<ClassNode>> superclassGroups = new HashMap<>();

        for (ClassNode classNode : classes) {
            if (classNode.superName != null) {
                superclassGroups
                    .computeIfAbsent(classNode.superName, k -> new ArrayList<>())
                    .add(classNode);
            }
        }

        return superclassGroups;
    }

    public Map<String, List<ClassNode>> groupByInterface(List<ClassNode> classes) {
        Map<String, List<ClassNode>> interfaceGroups = new HashMap<>();

        for (ClassNode classNode : classes) {
            if (classNode.interfaces != null) {
                for (String interfaceName : classNode.interfaces) {
                    interfaceGroups
                        .computeIfAbsent(interfaceName, k -> new ArrayList<>())
                        .add(classNode);
                }
            }
        }

        return interfaceGroups;
    }

    public Map<String, List<ClassNode>> findMatchingClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        Map<String, List<ClassNode>> matching = new HashMap<>();

        for (String jarName : jarClassesMap.keySet()) {
            if (jarName.equals(jarToExclude)) {
                continue;
            }

            List<ClassNode> classes = classComparator.getMatchingClassNodes(jarClassesMap.get(jarName), classNodeToMatch);
            matching.putIfAbsent(jarName, classes);
        }

        return matching;
    }

    private Map<String, List<ClassNode>> readJarFile(File file) throws IOException {
        JarLoader jarLoader = new JarLoader(stripAnnotations);
        return jarLoader.readClasses(file);
    }

    private ClassNode readClassFile(File file) throws IOException {
        ClassFileLoader classLoader = new ClassFileLoader(stripAnnotations);
        return classLoader.readClass(file);
    }

    /**
     * Returns an unmodifiable view of the current jar->classes mapping.
     * Both the map and the contained lists are unmodifiable.
     */
    public Map<String, List<ClassNode>> getJarClassesView() {
        Map<String, List<ClassNode>> copy = new HashMap<>();
        for (Map.Entry<String, List<ClassNode>> e : jarClassesMap.entrySet()) {
            copy.put(e.getKey(), java.util.Collections.unmodifiableList(e.getValue()));
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    /**
     * Removes an entire jar and all its classes from the store.
     *
     * @param jarName the jar key
     * @return true if a mapping existed and was removed
     */
    public boolean removeJar(String jarName) {
        return jarClassesMap.remove(jarName) != null;
    }

    /**
     * Removes a single class from a given jar group if present.
     *
     * @param jarName   jar key
     * @param classNode the class to remove
     * @return true if removed
     */
    public boolean removeClass(String jarName, ClassNode classNode) {
        List<ClassNode> list = jarClassesMap.get(jarName);
        if (list == null) return false;
        return list.remove(classNode);
    }

    /**
     * Returns the mutable list of classes for a given jar name, or null.
     * Prefer using getJarClassesView() for read-only iteration.
     */
    public List<ClassNode> getClasses(String jarName) {
        return jarClassesMap.get(jarName);
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticFieldDescriptions(classNode);
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classComparator.getNonStaticMethodDescriptions(classNode);
    }

    public boolean areMethodsBehaviorallyEquivalent(List<ClassNode> classNodes1, MethodNode methodNode1, List<ClassNode> classNodes2, MethodNode methodNode2) {
        return methodComparator.areBehaviorallyEquivalent(classNodes1, methodNode1, classNodes2, methodNode2);
    }

    public boolean isStripAnnotations() {
        return stripAnnotations;
    }

    public void setStripAnnotations(boolean stripAnnotations) {
        this.stripAnnotations = stripAnnotations;
    }

    private void startBackgroundJarDecompile(File jarFile) {
        String jarName = jarFile.getName();
        if (decompiledByJar.containsKey(jarName)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                logger.log("[Decompiler] Warming up sources for JAR: " + jarName);
                JetBrainsDecompilerService jetBrainsDecompilerService = new JetBrainsDecompilerService(logger);
                Map<String, String> sources = jetBrainsDecompilerService.decompileJarSources(jarFile);
                cacheDecompiledSources(jarName, sources);
                logger.log("[Decompiler] Warm-up complete for " + jarName + ", sources=" + sources.size());
            } catch (Throwable ex) {
                logger.log("[Decompiler] Warm-up failed for " + jarName + ": " + ex.getMessage());
            }
        }, "rikr-jar-decompiler-" + jarName);
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void cacheDecompiledSources(String jarName, Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        decompiledByJar.put(jarName, sources);

        for (Map.Entry<String, String> e : sources.entrySet()) {
            decompiledByClass.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    public String getDecompiledSourceByClass(String internalName) {
        if (internalName == null) {
            return null;
        }

        return decompiledByClass.get(internalName);
    }
}
