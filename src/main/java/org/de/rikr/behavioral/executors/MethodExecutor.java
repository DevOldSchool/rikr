package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.de.rikr.loader.ClassNodeClassLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MethodExecutor implements InstructionExecutor {
    private final ClassNodeClassLoader classLoader;

    public MethodExecutor(List<ClassNode> classNodes) {
        this.classLoader = new ClassNodeClassLoader(classNodes);
    }

    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        switch (instruction.getOpcode()) {
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
                return executeMethod((MethodInsnNode) instruction, instruction.getOpcode(), stack, localVariables, fieldValues);
        }

        return null;
    }

    private Object executeMethod(MethodInsnNode methodInsnNode, int instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        String owner = methodInsnNode.owner;
        String name = methodInsnNode.name;
        String descriptor = methodInsnNode.desc;

        MethodNode methodNode = findMethodNode(owner, name, descriptor);
        if (methodNode == null) {
            throw new RuntimeException("Method not found: " + owner + "." + name + descriptor);
        }

        // Prepare method arguments from stack
        Object[] arguments = null;
        if (methodNode.localVariables != null) {
            arguments = new Object[methodNode.localVariables.size()];
            for (int i = methodNode.localVariables.size() - 1; i >= 0; i--) {
                if (!stack.isEmpty()) {
                    arguments[i] = stack.pop();
                }
            }
        }

        // If it's an instance method, pop the object reference from stack
        Object objectRef = null;
        if (instruction != Opcodes.INVOKESTATIC && !stack.isEmpty()) {
            objectRef = stack.pop();
        }

        // Simulate method execution, update local variables
        if (arguments != null) {
            for (int i = 0; i < methodNode.localVariables.size(); i++) {
                Object value = arguments[i];
                localVariables.put(i, value);
            }
        }

        // Update field values if the method modifies any fields
        ClassNode classNode = null;
        try {
            classNode = classLoader.getClassNode(owner);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        for (String fieldName : fieldValues.keySet()) {
            if (classNode.fields.stream().anyMatch(field -> field.name.equals(fieldName))) {
                Object value = fieldValues.get(fieldName);

                // Update field value in the object reference or static context
                if (objectRef != null) {
                    setInstanceFieldValue(objectRef, fieldName, value);
                } else {
                    setStaticFieldValue(classNode.name, fieldName, value);
                }

                fieldValues.put(fieldName, value);
            }
        }

        return null;
    }

    private MethodNode findMethodNode(String owner, String name, String descriptor) {
        ClassNode classNode;
        try {
            classNode = classLoader.getClassNode(owner);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        if (classNode == null) {
            System.out.println("Class not found: " + owner + "." + name + descriptor);
            return null;
        }

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(name) && descriptor.equals(methodNode.desc)) {
                return methodNode;
            }
        }

        return null;
    }

    private void setInstanceFieldValue(Object object, String fieldName, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field value: " + fieldName, e);
        }
    }

    private void setStaticFieldValue(String className, String fieldName, Object value) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            // Pass null for static fields
            field.set(null, value);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set static field value: " + className + "." + fieldName, e);
        }
    }
}
