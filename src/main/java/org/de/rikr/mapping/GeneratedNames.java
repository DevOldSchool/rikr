package org.de.rikr.mapping;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recognises names that a deobfuscator made up rather than a person chose, such as {@code class12},
 * {@code field34} or {@code method56}, and the {@code func_123_a} style of SRG names. A JAR that was already
 * named keeps such names for whatever nobody named, and carrying them to another version would only rename one
 * placeholder to another.
 */
public final class GeneratedNames {
    private static final Pattern GENERATED = Pattern.compile(
        "(class|field|method|vmethod)\\d+|(func|field|method)_\\d+(_[A-Za-z]+)?_?");

    private static final Pattern LOWER_CASE = Pattern.compile("[a-z]+");
    private static final int LONGEST_OBFUSCATED = 3;

    private GeneratedNames() {
    }

    /**
     * Recognises the names an obfuscator gave, from the names of one JAR as it was loaded. Obfuscators give
     * nearly every class or member a name of the same few lower-case letters, such as "ao", so when most names
     * have one such length, names of that shape are taken as obfuscated. A JAR whose names were chosen by people
     * has no such majority, and nothing is taken as obfuscated.
     *
     * @param loadedNames the simple names of a JAR's classes, or of its fields and methods
     */
    public static Predicate<String> obfuscatedIn(Collection<String> loadedNames) {
        Map<Integer, Integer> byLength = new HashMap<>();
        for (String name : loadedNames) {
            if (name.length() <= LONGEST_OBFUSCATED && LOWER_CASE.matcher(name).matches()) {
                byLength.merge(name.length(), 1, Integer::sum);
            }
        }
        return byLength.entrySet().stream()
            .filter(entry -> entry.getValue() * 2 > loadedNames.size())
            .map(Map.Entry::getKey)
            .findFirst()
            .<Predicate<String>>map(length -> name -> name.length() == length && LOWER_CASE.matcher(name).matches())
            .orElse(name -> false);
    }

    /**
     * @param name a field or method name, or a class's internal name, whose package is ignored
     */
    public static boolean isGenerated(String name) {
        return GENERATED.matcher(name.substring(name.lastIndexOf('/') + 1)).matches();
    }
}
