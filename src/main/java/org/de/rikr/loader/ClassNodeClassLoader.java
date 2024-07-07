package org.de.rikr.loader;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassNodeClassLoader extends ClassLoader {
    private final Map<String, ClassNode> classNodeMap;

    public ClassNodeClassLoader(List<ClassNode> classNodes) {
        this.classNodeMap = new HashMap<>();

        for (ClassNode classNode : classNodes) {
            classNodeMap.put(classNode.name, classNode);
        }
    }

    public ClassNode getClassNode(String className) throws IOException, ClassNotFoundException {
        ClassNode classNode = classNodeMap.get(className);

        if (classNode == null) {
            classNode = loadClassNodeFromResource(className);
            classNodeMap.put(className, classNode);
        }

        return classNodeMap.get(className);
    }

    private ClassNode loadClassNodeFromResource(String className) throws IOException, ClassNotFoundException {
        String resourcePath = className.replace('.', '/') + ".class";
        try (InputStream inputStream = getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ClassNotFoundException("Class not found: " + className);
            }

            ClassReader classReader = new ClassReader(inputStream);
            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, ClassReader.SKIP_FRAMES);

            return classNode;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        ClassNode classNode = classNodeMap.get(name.replace('.', '/'));
        if (classNode != null) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(classWriter);
            byte[] classBytes = classWriter.toByteArray();

            System.out.println("Defining class: " + name);

            return defineClass(name, classBytes, 0, classBytes.length);
        } else {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException e) {
                try {
                    classNode = loadClassNodeFromResource(name.replace('.', '/'));
                    classNodeMap.put(name.replace('.', '/'), classNode);

                    return findClass(name);
                } catch (IOException ioException) {
                    throw new ClassNotFoundException("Class not found: " + name, ioException);
                }
            }
        }
    }
}
