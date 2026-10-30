package org.de.rikr.jar;

import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ClassLoadingService {
    public LoadedJar readJar(File file, boolean stripAnnotations) throws IOException {
        JarLoader jarLoader = new JarLoader(stripAnnotations);
        Map<String, List<ClassNode>> classesByJar = jarLoader.readClasses(file);
        String jarName = file.getName();

        return new LoadedJar(jarName, classesByJar.getOrDefault(jarName, List.of()), file.getAbsoluteFile());
    }

    public ClassNode readClass(File file, boolean stripAnnotations) throws IOException {
        ClassFileLoader classLoader = new ClassFileLoader(stripAnnotations);

        return classLoader.readClass(file);
    }
}
