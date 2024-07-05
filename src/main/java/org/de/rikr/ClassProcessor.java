package org.de.rikr;

import org.de.rikr.loader.ClassLoader;
import org.de.rikr.loader.JarLoader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassProcessor {
    private final Logger logger;
    private final Map<String, List<ClassNode>> jarClassesMap = new HashMap<>();

    public ClassProcessor(Logger logger) {
        this.logger = logger;
    }

    public void processJarFiles(File[] jarFiles) {
        for (File file : jarFiles) {
            try {
                Map<String, List<ClassNode>> singleJarClassesMap = readJarFile(file);
                jarClassesMap.put(file.getName(), singleJarClassesMap.get(file.getName()));
                logger.log("Loaded " + singleJarClassesMap.get(file.getName()).size() + " classes from " + file.getName());
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

    private Map<String, List<ClassNode>> readJarFile(File file) throws IOException {
        JarLoader jarLoader = new JarLoader();
        return jarLoader.readClasses(file);
    }

    private ClassNode readClassFile(File file) throws IOException {
        ClassLoader classLoader = new ClassLoader();
        return classLoader.readClass(file);
    }

    public Map<String, List<ClassNode>> getJarClassesMap() {
        return jarClassesMap;
    }
}
