package org.de.rikr.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassLoader {

    public ClassNode readClass(File classFile) throws IOException {
        ClassNode classNode = new ClassNode();
        try (InputStream inputStream = new FileInputStream(classFile)) {
            ClassReader classReader = new ClassReader(inputStream);
            classReader.accept(classNode, 0);
        }

        return classNode;
    }
}
