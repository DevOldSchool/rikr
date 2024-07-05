package org.de.rikr;

import org.de.rikr.loader.ClassLoader;
import org.de.rikr.loader.JarLoader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Map<String, List<ClassNode>> findMatchingClassNodes(String jarToExclude, ClassNode classNodeToMatch) {
        Map<String, List<ClassNode>> matching = new HashMap<>();

        boolean isInterfaceToMatch = Modifier.isInterface(classNodeToMatch.access);
        boolean isAbstractToMatch = Modifier.isAbstract(classNodeToMatch.access);
        boolean hasSuperClassToMatch = !classNodeToMatch.superName.equals("java/lang/Object");
        int interfaceCountToMatch = classNodeToMatch.interfaces.size();
        List<String> fieldsToMatch = getNonStaticFieldDescriptions(classNodeToMatch);
        List<String> methodsToMatch = getNonStaticMethodDescriptions(classNodeToMatch);

        for (String jarName : jarClassesMap.keySet()) {
            if (jarName.equals(jarToExclude)) {
                continue;
            }

            List<ClassNode> classes = jarClassesMap.get(jarName);

            for (ClassNode classNode : classes) {
                if (matches(classNode, isInterfaceToMatch, isAbstractToMatch, hasSuperClassToMatch, interfaceCountToMatch, fieldsToMatch, methodsToMatch)) {
                    matching.putIfAbsent(jarName, new ArrayList<>());
                    
                    if (!matching.get(jarName).contains(classNode)) {
                        matching.get(jarName).add(classNode);
                    }
                }
            }
        }

        return matching;
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

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classNode.fields.stream()
                .filter(field -> !Modifier.isStatic(field.access))
                .map(field -> field.desc.replaceAll("L.*?;", "L?;"))
                .collect(Collectors.toList());
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classNode.methods.stream()
                .filter(method -> !Modifier.isStatic(method.access))
                .map(method -> method.desc.replaceAll("L.*?;", "L?;"))
                .collect(Collectors.toList());
    }

    private boolean matches(ClassNode classNode, boolean isInterfaceToMatch, boolean isAbstractToMatch, boolean hasSuperClassToMatch, int interfaceCountToMatch, List<String> fieldsToMatch, List<String> methodsToMatch) {
        boolean isInterface = Modifier.isInterface(classNode.access);
        boolean isAbstract = Modifier.isAbstract(classNode.access);
        boolean hasSuperClass = !classNode.superName.equals("java/lang/Object");
        int interfaceCount = classNode.interfaces.size();
        List<String> fields = getNonStaticFieldDescriptions(classNode);
        List<String> methods = getNonStaticMethodDescriptions(classNode);

        return isInterface == isInterfaceToMatch &&
                isAbstract == isAbstractToMatch &&
                hasSuperClass == hasSuperClassToMatch &&
                interfaceCount == interfaceCountToMatch &&
                fields.equals(fieldsToMatch) &&
                methods.equals(methodsToMatch);
    }
}
