package org.de.rikr.rename;

import org.de.rikr.mapping.GeneratedNames;
import org.objectweb.asm.tree.ClassNode;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * How far naming a JAR's classes has come, and which unnamed classes to name next: those referred to most, since
 * their names make the most code readable.
 * <p>
 * A class is unnamed while it still has the name an obfuscator gave it, or a made-up name such as {@code class12}.
 * Obfuscated names are recognised from the names as loaded (see {@link GeneratedNames#obfuscatedIn}). When a JAR has
 * classes in no package, only those are counted: an obfuscator moves a program's classes out of their packages,
 * and the classes left in packages are libraries bundled with it.
 */
public final class NamingWorklist {
    private NamingWorklist() {
    }

    /**
     * An unnamed class and how many places refer to it.
     */
    public record Entry(ClassNode classNode, int references) {
    }

    /**
     * @param classes the classes counted
     * @param named   how many of them have a name a person gave
     * @param next    unnamed classes, most referred to first
     */
    public record Progress(int classes, int named, List<Entry> next) {
    }

    /**
     * @param originalName a class's internal name as it was loaded
     * @param references   how many places refer to a class; asked only of unnamed classes
     * @param limit        how many unnamed classes to list at most
     */
    public static Progress of(List<ClassNode> classes, Function<ClassNode, String> originalName,
                              ToIntFunction<ClassNode> references, int limit) {
        List<ClassNode> inNoPackage = classes.stream()
            .filter(classNode -> originalName.apply(classNode).indexOf('/') < 0)
            .toList();
        List<ClassNode> counted = inNoPackage.isEmpty() ? classes : inNoPackage;
        Predicate<String> obfuscated = GeneratedNames.obfuscatedIn(counted.stream()
            .map(classNode -> simpleName(originalName.apply(classNode)))
            .toList());
        List<ClassNode> unnamed = counted.stream()
            .filter(classNode -> isUnnamed(classNode, originalName.apply(classNode), obfuscated))
            .toList();
        List<Entry> next = unnamed.stream()
            .map(classNode -> new Entry(classNode, references.applyAsInt(classNode)))
            .sorted(Comparator.comparingInt(Entry::references).reversed()
                .thenComparing(entry -> entry.classNode().name))
            .limit(limit)
            .toList();
        return new Progress(counted.size(), counted.size() - unnamed.size(), next);
    }

    private static boolean isUnnamed(ClassNode classNode, String original, Predicate<String> obfuscated) {
        boolean keepsObfuscatedName = classNode.name.equals(original) && obfuscated.test(simpleName(original));
        return keepsObfuscatedName || GeneratedNames.isGenerated(classNode.name);
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }
}
