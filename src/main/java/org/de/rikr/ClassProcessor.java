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
    private final Map<String, List<ClassNode>> jarClassesMap = new HashMap<>();

    public void processJarFiles(File[] jarFiles) {
        for (File file : jarFiles) {
            try {
                Map<String, List<ClassNode>> singleJarClassesMap = readJarFile(file);
                jarClassesMap.put(file.getName(), singleJarClassesMap.get(file.getName()));
            } catch (IOException e) {
                System.err.println("Error processing jar file: " + file.getName());
                e.printStackTrace();
            }
        }
    }

    public void processClassFiles(File[] classFiles) {
        for (File file : classFiles) {
            try {
                ClassNode classNode = readClassFile(file);
                jarClassesMap.computeIfAbsent("Classes", k -> new ArrayList<>()).add(classNode);
            } catch (IOException e) {
                System.err.println("Error processing class file: " + file.getName());
                e.printStackTrace();
            }
        }
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
