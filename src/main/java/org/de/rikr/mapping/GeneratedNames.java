package org.de.rikr.mapping;

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

    private GeneratedNames() {
    }

    /**
     * @param name a field or method name, or a class's internal name, whose package is ignored
     */
    public static boolean isGenerated(String name) {
        return GENERATED.matcher(name.substring(name.lastIndexOf('/') + 1)).matches();
    }
}
