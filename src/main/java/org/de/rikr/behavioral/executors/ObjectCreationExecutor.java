package org.de.rikr.behavioral.executors;

import org.de.rikr.behavioral.InstructionExecutor;
import org.de.rikr.loader.ClassNodeClassLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ObjectCreationExecutor implements InstructionExecutor {
    private final ClassNodeClassLoader classLoader;

    public ObjectCreationExecutor(List<ClassNode> classNodes) {
        this.classLoader = new ClassNodeClassLoader(classNodes);
    }

    @Override
    public Object execute(AbstractInsnNode instruction, Stack<Object> stack, Map<Integer, Object> localVariables, Map<String, Object> fieldValues) {
        String descriptor;
        Object obj;
        int count;

        switch (instruction.getOpcode()) {
            case Opcodes.NEW:
                descriptor = ((TypeInsnNode) instruction).desc;


                try {
                    // TODO readme, intentionally not creating new instances for easier stack comparison.
//                    Class<?> clazz = classLoader.loadClass(descriptor.replace('/', '.'));
                    Class<?> clazz = classLoader.loadClass("java.lang.Object");

                    // Check for a no-argument constructor
                    Constructor<?> constructor;
                    try {
                        constructor = clazz.getDeclaredConstructor();
                    } catch (NoSuchMethodException e) {
                        break;
                    }

                    // Ensure the constructor is accessible
                    constructor.setAccessible(true);

                    Object newInstance = constructor.newInstance();
                    stack.push(newInstance);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to create new instance of: " + descriptor, e);
                }

                break;
            case Opcodes.NEWARRAY:
                if (stack.isEmpty()) {
                    return null;
                }

                obj = stack.pop();
                if (obj == null) {
                    return null;
                }

                if (obj instanceof Integer) {
                    count = (int) obj;
                } else if (obj instanceof Long) {
                    count = ((Long) obj).intValue();
                } else {
                    return null;
                }

                if (count < 0) {
                    throw new NegativeArraySizeException("Array size cannot be negative: " + count);
                }

                int arrayType = ((IntInsnNode) instruction).operand;
                Object newArray = switch (arrayType) {
                    case Opcodes.T_BOOLEAN -> new boolean[count];
                    case Opcodes.T_CHAR -> new char[count];
                    case Opcodes.T_FLOAT -> new float[count];
                    case Opcodes.T_DOUBLE -> new double[count];
                    case Opcodes.T_BYTE -> new byte[count];
                    case Opcodes.T_SHORT -> new short[count];
                    case Opcodes.T_INT -> new int[count];
                    case Opcodes.T_LONG -> new long[count];
                    default -> throw new UnsupportedOperationException("Unsupported array type: " + arrayType);
                };

                stack.push(newArray);
                break;
            case Opcodes.ANEWARRAY:
                if (stack.isEmpty()) {
                    return null;
                }

                obj = stack.pop();
                if (obj == null) {
                    return null;
                }

                if (obj instanceof Integer) {
                    count = (int) obj;
                } else if (obj instanceof Long) {
                    count = ((Long) obj).intValue();
                } else {
                    return null;
                }

                if (count < 0) {
                    throw new NegativeArraySizeException("Array size cannot be negative: " + count);
                }

                descriptor = ((TypeInsnNode) instruction).desc;


                try {
                    // TODO readme, intentionally not creating new instances for easier stack comparison.
//                    Class<?> clazz = classLoader.loadClass(descriptor.replace('/', '.'));
                    Class<?> clazz = classLoader.loadClass("java.lang.Object");

                    // Check for a no-argument constructor
                    Constructor<?> constructor;
                    try {
                        constructor = clazz.getDeclaredConstructor();
                    } catch (NoSuchMethodException e) {
                        break;
                    }

                    // Ensure the constructor is accessible
                    constructor.setAccessible(true);

                    Object referenceArray = java.lang.reflect.Array.newInstance(clazz, count);

                    stack.push(referenceArray);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Class not found: " + descriptor, e);
                }

                break;
        }

        return null;
    }
}
