package org.de.rikr.mapping.format;

import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads mapping files into {@link NameMappings}, from the original (obfuscated) names to the named ones.
 */
public final class MappingReader {
    private MappingReader() {
    }

    /**
     * Recognise the format from the file's content: tiny v2 starts with its header, Enigma with {@code CLASS},
     * and ProGuard lines map classes with {@code " -> "} and end in a colon.
     */
    public static MappingFormat detect(String text) throws IOException {
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (line.startsWith("tiny\t")) {
                return MappingFormat.TINY_V2;
            }
            if (trimmed.startsWith("CLASS ")) {
                return MappingFormat.ENIGMA;
            }
            if (trimmed.contains(" -> ") && trimmed.endsWith(":")) {
                return MappingFormat.PROGUARD;
            }
            break;
        }
        throw new IOException("Not a tiny v2, Enigma or ProGuard mapping file.");
    }

    public static NameMappings read(String text) throws IOException {
        return read(detect(text), text);
    }

    public static NameMappings read(MappingFormat format, String text) throws IOException {
        return switch (format) {
            case TINY_V2 -> readTiny(text);
            case ENIGMA -> readEnigma(text);
            case PROGUARD -> readProguard(text);
        };
    }

    /**
     * Tiny v2. The first namespace is taken as the original names, and {@code named} (or else the last
     * namespace) as the new names. An empty name means the name does not change.
     */
    private static NameMappings readTiny(String text) throws IOException {
        String[] lines = text.split("\\R");
        String[] header = lines[0].split("\t", -1);
        if (header.length < 5 || !header[0].equals("tiny") || !header[1].equals("2")) {
            throw new IOException("Not a tiny v2 file: the header must be 'tiny 2 0 <namespaces...>'.");
        }
        int named = header.length - 1;
        for (int index = 3; index < header.length; index++) {
            if (header[index].equals("named")) {
                named = index;
            }
        }
        int namedColumn = named - 3;

        Builder builder = new Builder();
        for (int number = 1; number < lines.length; number++) {
            String line = lines[number];
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts[0].equals("c")) {
                builder.startClass(column(parts, 1, 0, number), column(parts, 1, namedColumn, number));
            } else if (parts.length > 2 && parts[0].isEmpty() && (parts[1].equals("f") || parts[1].equals("m"))) {
                String descriptor = parts[2];
                String original = column(parts, 3, 0, number);
                String newName = column(parts, 3, namedColumn, number);
                builder.member(parts[1].equals("f"), original, descriptor, newName, number);
            }
            // Comments, parameters and local variables are not renamed by Rikr.
        }
        return builder.build();
    }

    private static String column(String[] parts, int first, int column, int number) throws IOException {
        int index = first + column;
        if (index >= parts.length) {
            throw new IOException("Line " + (number + 1) + " has too few names.");
        }
        String value = parts[index];
        return value.isEmpty() ? parts[first] : value;
    }

    /**
     * Enigma. Inner classes are nested under their outer class by indentation and use simple names.
     */
    private static NameMappings readEnigma(String text) throws IOException {
        Builder builder = new Builder();
        List<String[]> outer = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int number = 0; number < lines.length; number++) {
            String line = lines[number];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '\t') {
                depth++;
            }
            String[] parts = trimmed.split(" ");
            switch (parts[0]) {
                case "CLASS" -> {
                    while (outer.size() > depth) {
                        outer.removeLast();
                    }
                    String original = parts[1];
                    String newName = parts.length > 2 ? parts[2] : null;
                    if (!outer.isEmpty()) {
                        String[] parent = outer.getLast();
                        original = parent[0] + "$" + original;
                        newName = parent[1] + "$" + (newName == null ? parts[1] : newName);
                    }
                    if (newName == null) {
                        newName = original;
                    }
                    outer.add(new String[]{original, newName});
                    builder.startClass(original, newName);
                }
                case "FIELD", "METHOD" -> {
                    if (parts.length < 3) {
                        throw new IOException("Line " + (number + 1) + " is missing a descriptor.");
                    }
                    // A member belongs to the class one level less indented.
                    while (outer.size() > depth) {
                        outer.removeLast();
                    }
                    if (outer.isEmpty()) {
                        throw new IOException("Line " + (number + 1) + " names a member before any class.");
                    }
                    String[] owner = outer.getLast();
                    builder.startClass(owner[0], owner[1]);
                    String newName = parts.length > 3 ? parts[2] : parts[1];
                    String descriptor = parts.length > 3 ? parts[3] : parts[2];
                    builder.member(parts[0].equals("FIELD"), parts[1], descriptor, newName, number);
                }
                default -> {
                    // ARG and COMMENT lines are not renamed by Rikr.
                }
            }
        }
        return builder.build();
    }

    /**
     * ProGuard. The file goes from named Java names to original names, and member types are named Java types,
     * so descriptors are converted to original class names once every class is known.
     */
    private static NameMappings readProguard(String text) throws IOException {
        record Member(boolean field, String javaType, String arguments, String namedName, String original) {
        }
        Map<String, String> namedToOriginal = new HashMap<>();
        Map<String, String> classes = new LinkedHashMap<>();
        Map<String, List<Member>> members = new LinkedHashMap<>();
        String current = null;
        String[] lines = text.split("\\R");
        for (int number = 0; number < lines.length; number++) {
            String line = lines[number];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int arrow = trimmed.indexOf(" -> ");
            if (arrow < 0) {
                throw new IOException("Line " + (number + 1) + " has no ' -> '.");
            }
            String left = trimmed.substring(0, arrow);
            String right = trimmed.substring(arrow + 4);
            if (!Character.isWhitespace(line.charAt(0))) {
                if (!right.endsWith(":")) {
                    throw new IOException("Line " + (number + 1) + " is not a class mapping.");
                }
                String named = left.replace('.', '/');
                current = right.substring(0, right.length() - 1).replace('.', '/');
                namedToOriginal.put(named, current);
                classes.put(current, named);
                members.put(current, new ArrayList<>());
                continue;
            }
            if (current == null) {
                throw new IOException("Line " + (number + 1) + " names a member before any class.");
            }
            // Strip line numbers: "12:34:void run():56:78" becomes "void run()".
            String declaration = left.replaceFirst("^\\d+:\\d+:", "").replaceFirst(":\\d+(:\\d+)?$", "");
            int space = declaration.indexOf(' ');
            String type = declaration.substring(0, space);
            String rest = declaration.substring(space + 1);
            int open = rest.indexOf('(');
            if (open < 0) {
                members.get(current).add(new Member(true, type, null, rest, right));
            } else {
                String name = rest.substring(0, open);
                if (name.contains(".")) {
                    // An inlined method of another class.
                    continue;
                }
                members.get(current).add(new Member(false, type, rest.substring(open + 1, rest.length() - 1), name, right));
            }
        }

        Builder builder = new Builder();
        for (Map.Entry<String, String> entry : classes.entrySet()) {
            builder.startClass(entry.getKey(), entry.getValue());
            for (Member member : members.get(entry.getKey())) {
                String returnType = descriptor(member.javaType(), namedToOriginal);
                String descriptor;
                if (member.field()) {
                    descriptor = returnType;
                } else {
                    StringBuilder arguments = new StringBuilder("(");
                    if (!member.arguments().isEmpty()) {
                        for (String argument : member.arguments().split(",")) {
                            arguments.append(descriptor(argument.strip(), namedToOriginal));
                        }
                    }
                    descriptor = arguments.append(')').append(returnType).toString();
                }
                builder.member(member.field(), member.original(), descriptor, member.namedName(), -1);
            }
        }
        return builder.build();
    }

    private static String descriptor(String javaType, Map<String, String> namedToOriginal) {
        int dimensions = 0;
        String type = javaType;
        while (type.endsWith("[]")) {
            dimensions++;
            type = type.substring(0, type.length() - 2);
        }
        String element = switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                String internal = type.replace('.', '/');
                yield Type.getObjectType(namedToOriginal.getOrDefault(internal, internal)).getDescriptor();
            }
        };
        return "[".repeat(dimensions) + element;
    }

    /**
     * Collects classes in file order; members belong to the class started last. Starting a class again continues
     * it, so a class split over several places in a file becomes one mapping.
     */
    private static final class Builder {
        private record Entry(String named, List<NameMappings.MemberMapping> fields, List<NameMappings.MemberMapping> methods) {
        }

        private final Map<String, Entry> classes = new LinkedHashMap<>();
        private Entry current;

        void startClass(String originalName, String namedName) {
            current = classes.computeIfAbsent(originalName, name -> new Entry(namedName, new ArrayList<>(), new ArrayList<>()));
        }

        void member(boolean field, String originalName, String descriptor, String newName, int number)
            throws IOException {
            if (current == null) {
                throw new IOException("Line " + (number + 1) + " names a member before any class.");
            }
            (field ? current.fields() : current.methods())
                .add(new NameMappings.MemberMapping(originalName, descriptor, newName));
        }

        NameMappings build() {
            List<NameMappings.ClassMapping> mappings = new ArrayList<>();
            classes.forEach((original, entry) ->
                mappings.add(new NameMappings.ClassMapping(original, entry.named(), entry.fields(), entry.methods())));
            return new NameMappings(mappings);
        }
    }
}
