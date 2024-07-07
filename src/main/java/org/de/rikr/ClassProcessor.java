package org.de.rikr;

import org.de.rikr.behavioral.ClassComparator;
import org.de.rikr.behavioral.MethodComparator;
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

    public ClassProcessor(Logger logger) {
        this.logger = logger;

        jarClassesMap = new HashMap<>();
        classComparator = new ClassComparator();
        methodComparator = new MethodComparator();
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
        JarLoader jarLoader = new JarLoader();
        return jarLoader.readClasses(file);
    }

    private ClassNode readClassFile(File file) throws IOException {
        ClassFileLoader classLoader = new ClassFileLoader();
        return classLoader.readClass(file);
    }

    public Map<String, List<ClassNode>> getJarClassesMap() {
        return jarClassesMap;
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
}
