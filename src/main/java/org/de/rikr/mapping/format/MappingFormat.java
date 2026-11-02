package org.de.rikr.mapping.format;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

/**
 * Mapping file formats that rename mappings can be exported in.
 */
public enum MappingFormat {
    /**
     * Fabric's tiny v2, with the namespaces {@code official} (original) and {@code named}.
     */
    TINY_V2("Tiny v2", "tiny"),
    /**
     * Enigma's single-file format. Every class is written at the top level, by its full name.
     */
    ENIGMA("Enigma", "mapping"),
    /**
     * ProGuard's mapping.txt, which goes from named Java names to original names.
     */
    PROGUARD("ProGuard", "txt");

    private final String displayName;
    private final String extension;

    MappingFormat(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public String displayName() {
        return displayName;
    }

    public String extension() {
        return extension;
    }

    public String write(NameMappings mappings) {
        StringBuilder out = new StringBuilder();
        switch (this) {
            case TINY_V2 -> writeTiny(mappings, out);
            case ENIGMA -> writeEnigma(mappings, out);
            case PROGUARD -> writeProguard(mappings, out);
        }
        return out.toString();
    }

    private static void writeTiny(NameMappings mappings, StringBuilder out) {
        out.append("tiny\t2\t0\tofficial\tnamed\n");
        for (NameMappings.ClassMapping mapping : mappings.classes()) {
            out.append("c\t").append(mapping.original()).append('\t').append(mapping.named()).append('\n');
            for (NameMappings.MemberMapping field : mapping.fields()) {
                out.append("\tf\t").append(field.descriptor()).append('\t').append(field.original()).append('\t')
                    .append(field.named()).append('\n');
            }
            for (NameMappings.MemberMapping method : mapping.methods()) {
                out.append("\tm\t").append(method.descriptor()).append('\t').append(method.original()).append('\t')
                    .append(method.named()).append('\n');
            }
        }
    }

    private static void writeEnigma(NameMappings mappings, StringBuilder out) {
        for (NameMappings.ClassMapping mapping : mappings.classes()) {
            out.append("CLASS ").append(mapping.original());
            if (!mapping.named().equals(mapping.original())) {
                out.append(' ').append(mapping.named());
            }
            out.append('\n');
            for (NameMappings.MemberMapping field : mapping.fields()) {
                out.append("\tFIELD ").append(field.original()).append(' ').append(field.named()).append(' ')
                    .append(field.descriptor()).append('\n');
            }
            for (NameMappings.MemberMapping method : mapping.methods()) {
                out.append("\tMETHOD ").append(method.original()).append(' ').append(method.named()).append(' ')
                    .append(method.descriptor()).append('\n');
            }
        }
    }

    private static void writeProguard(NameMappings mappings, StringBuilder out) {
        Remapper toNamed = mappings.toNamed();
        for (NameMappings.ClassMapping mapping : mappings.classes()) {
            out.append(javaName(mapping.named())).append(" -> ").append(javaName(mapping.original())).append(":\n");
            for (NameMappings.MemberMapping field : mapping.fields()) {
                out.append("    ").append(Type.getType(toNamed.mapDesc(field.descriptor())).getClassName())
                    .append(' ').append(field.named()).append(" -> ").append(field.original()).append('\n');
            }
            for (NameMappings.MemberMapping method : mapping.methods()) {
                Type type = Type.getMethodType(toNamed.mapMethodDesc(method.descriptor()));
                out.append("    ").append(type.getReturnType().getClassName()).append(' ').append(method.named())
                    .append('(');
                Type[] arguments = type.getArgumentTypes();
                for (int index = 0; index < arguments.length; index++) {
                    if (index > 0) {
                        out.append(',');
                    }
                    out.append(arguments[index].getClassName());
                }
                out.append(") -> ").append(method.original()).append('\n');
            }
        }
    }

    private static String javaName(String internalName) {
        return internalName.replace('/', '.');
    }
}
