package org.de.rikr.flow;

import org.de.rikr.rename.ClassHierarchy;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Finds where a JAR's code is started from. Obfuscated JARs rarely say so directly: a game client is often an
 * applet with no {@code main}, so applet lifecycles, thread bodies and event listeners are found as well.
 */
public final class EntryPoints {
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";
    private static final List<String> APPLET_LIFECYCLE = List.of("init", "start", "stop", "destroy");

    private EntryPoints() {
    }

    /**
     * Every entry point found, most likely first.
     *
     * @param manifest the JAR's manifest, or null if it has none
     */
    public static List<EntryPoint> find(List<ClassNode> classes, Manifest manifest) {
        ClassHierarchy hierarchy = new ClassHierarchy(classes);
        Map<MethodRef, EntryPoint> found = new LinkedHashMap<>();
        String mainClass = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        String mainClassName = mainClass == null ? null : mainClass.strip().replace('.', '/');

        List<ClassNode> sorted = classes.stream().sorted(Comparator.comparing(classNode -> classNode.name)).toList();
        for (ClassNode classNode : sorted) {
            for (MethodNode method : classNode.methods) {
                if (method.name.equals("main") && method.desc.equals(MAIN_DESCRIPTOR)
                    && (method.access & Opcodes.ACC_STATIC) != 0) {
                    boolean manifestMain = classNode.name.equals(mainClassName);
                    add(found, new EntryPoint(ref(classNode, method),
                        manifestMain ? EntryPoint.Kind.MAIN_CLASS : EntryPoint.Kind.MAIN,
                        manifestMain ? "Main-Class in the manifest" : "a static main method"));
                }
            }
        }
        for (ClassNode classNode : sorted) {
            if ((classNode.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) {
                continue;
            }
            boolean applet = isSubtype(hierarchy, classNode.name, "java/applet/Applet");
            boolean appletLike = APPLET_LIFECYCLE.stream()
                .allMatch(name -> hierarchy.resolveMethod(classNode.name, name, "()V") != null);
            if (applet || appletLike) {
                String reason = applet ? "an applet: the browser calls init(), then start()"
                    : "has an applet's init, start, stop and destroy: a loader calls init(), then start()";
                for (String name : List.of("init", "start")) {
                    String declaring = hierarchy.resolveMethod(classNode.name, name, "()V");
                    if (declaring != null) {
                        add(found, new EntryPoint(new MethodRef(declaring, name, "()V"), EntryPoint.Kind.APPLET,
                            reason + (declaring.equals(classNode.name) ? "" : " (declared in " + declaring + ")")));
                    }
                }
                // An applet that is its own Runnable usually starts a thread on itself: that run() is the main loop.
                String run = hierarchy.resolveMethod(classNode.name, "run", "()V");
                if (run != null && isSubtype(hierarchy, classNode.name, "java/lang/Runnable")) {
                    add(found, new EntryPoint(new MethodRef(run, "run", "()V"), EntryPoint.Kind.THREAD,
                        "the applet's own run(): its main loop when start() runs it on a thread"));
                }
            }
        }
        for (ClassNode classNode : sorted) {
            if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                continue;
            }
            if (isSubtype(hierarchy, classNode.name, "java/lang/Runnable")) {
                MethodNode run = ClassHierarchy.findMethod(classNode, "run", "()V");
                if (run != null && (run.access & Opcodes.ACC_ABSTRACT) == 0) {
                    add(found, new EntryPoint(ref(classNode, run), EntryPoint.Kind.THREAD,
                        "run() of a Runnable or Thread: a thread runs it"));
                }
            }
        }
        for (ClassNode classNode : sorted) {
            if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                continue;
            }
            for (String listener : externalListeners(hierarchy, classNode.name)) {
                for (MethodNode method : classNode.methods) {
                    if (declaresListenerMethod(listener, method)) {
                        add(found, new EntryPoint(ref(classNode, method), EntryPoint.Kind.LISTENER,
                            "implements " + listener.replace('/', '.') + ": the windowing system calls it"));
                    }
                }
            }
        }
        return found.values().stream()
            .sorted(Comparator.comparing(EntryPoint::kind))
            .toList();
    }

    private static void add(Map<MethodRef, EntryPoint> found, EntryPoint entry) {
        found.putIfAbsent(entry.method(), entry);
    }

    private static MethodRef ref(ClassNode owner, MethodNode method) {
        return new MethodRef(owner.name, method.name, method.desc);
    }

    /**
     * Whether the class extends or implements {@code target}, looking through JDK supertypes as well.
     */
    static boolean isSubtype(ClassHierarchy hierarchy, String className, String target) {
        Class<?> targetType = load(target);
        for (String ancestor : hierarchy.selfAndAncestors(className)) {
            if (ancestor.equals(target)) {
                return true;
            }
            if (!hierarchy.contains(ancestor) && targetType != null) {
                Class<?> external = load(ancestor);
                if (external != null && targetType.isAssignableFrom(external)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The JDK event listener interfaces the class implements, directly or through other types.
     */
    private static List<String> externalListeners(ClassHierarchy hierarchy, String className) {
        Class<?> eventListener = load("java/util/EventListener");
        List<String> listeners = new ArrayList<>();
        for (String ancestor : hierarchy.selfAndAncestors(className)) {
            if (hierarchy.contains(ancestor)) {
                continue;
            }
            Class<?> external = load(ancestor);
            if (external != null && external.isInterface() && eventListener != null
                && eventListener.isAssignableFrom(external) && external != eventListener) {
                listeners.add(ancestor);
            }
        }
        return listeners;
    }

    private static boolean declaresListenerMethod(String listener, MethodNode method) {
        Class<?> type = load(listener);
        if (type == null || (method.access & Opcodes.ACC_STATIC) != 0) {
            return false;
        }
        for (Method declared : type.getMethods()) {
            if (!Modifier.isStatic(declared.getModifiers()) && declared.getName().equals(method.name)
                && Type.getMethodDescriptor(declared).equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    private static Class<?> load(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }
}
