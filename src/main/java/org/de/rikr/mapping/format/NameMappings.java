package org.de.rikr.mapping.format;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Names of one JAR as it was loaded ("original") and as it is now ("named"). Descriptors are always in original
 * class names, as the mapping formats expect.
 */
public record NameMappings(List<ClassMapping> classes) {
    public NameMappings {
        classes = List.copyOf(classes);
    }

    public record ClassMapping(String original, String named, List<MemberMapping> fields, List<MemberMapping> methods) {
        public ClassMapping {
            fields = List.copyOf(fields);
            methods = List.copyOf(methods);
        }
    }

    public record MemberMapping(String original, String descriptor, String named) {
    }

    /**
     * Turns original class names into named ones, for formats that write descriptors in named classes.
     */
    Remapper toNamed() {
        Map<String, String> names = new HashMap<>();
        for (ClassMapping mapping : classes) {
            names.put(mapping.original(), mapping.named());
        }
        return new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String internalName) {
                return names.getOrDefault(internalName, internalName);
            }
        };
    }
}
