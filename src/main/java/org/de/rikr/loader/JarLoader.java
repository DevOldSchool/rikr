package org.de.rikr.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarLoader {

    public Map<String, List<ClassNode>> readClasses(File jarFile) throws IOException {
        Map<String, List<ClassNode>> jarClassesMap = new HashMap<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().endsWith(".class")) {
                    String jarName = jarFile.getName();
                    ClassNode classNode = new ClassNode();
                    ClassReader classReader = new ClassReader(jar.getInputStream(entry));
                    classReader.accept(classNode, ClassReader.SKIP_FRAMES);
                    jarClassesMap.computeIfAbsent(jarName, k -> new ArrayList<>()).add(classNode);
                }
            }
        }

        return jarClassesMap;
    }
}
