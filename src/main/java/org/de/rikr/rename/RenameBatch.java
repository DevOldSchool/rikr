package org.de.rikr.rename;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renames of one JAR that are applied together, as when a mapping file is imported. Every name is as it was
 * before the batch, so the renames do not depend on each other's order and may even swap names.
 * <p>
 * Methods are listed once per declaring class: when a method is overridden, every declaration in its family is
 * listed with the same new name.
 *
 * @param source   where the renames came from, such as the mapping file's name
 * @param classes  old class name to new class name
 */
public record RenameBatch(
    String jarName,
    String source,
    Map<String, String> classes,
    List<MemberRename> fields,
    List<MemberRename> methods
) implements RenameStep {
    public RenameBatch {
        Objects.requireNonNull(jarName, "jarName");
        Objects.requireNonNull(source, "source");
        classes = Map.copyOf(classes);
        fields = List.copyOf(fields);
        methods = List.copyOf(methods);
    }

    /**
     * A field or method declaration in {@code owner}, named {@code name} with {@code descriptor}, renamed to
     * {@code newName}.
     */
    public record MemberRename(String owner, String name, String descriptor, String newName) {
        public MemberRename {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(newName, "newName");
        }
    }

    public int size() {
        return classes.size() + fields.size() + methods.size();
    }

    /**
     * Maps old class names to new ones, leaving other names alone.
     */
    public Remapper classRemapper() {
        return new Remapper(Opcodes.ASM9) {
            @Override
            public String map(String internalName) {
                return classes.getOrDefault(internalName, internalName);
            }
        };
    }

    /**
     * The batch that undoes this one, written in the names this batch produces.
     */
    public RenameBatch inverse() {
        Remapper remapper = classRemapper();
        Map<String, String> inverseClasses = new LinkedHashMap<>();
        classes.forEach((oldName, newName) -> inverseClasses.put(newName, oldName));
        return new RenameBatch(
            jarName,
            source,
            inverseClasses,
            fields.stream()
                .map(field -> new MemberRename(remapper.map(field.owner()), field.newName(),
                    remapper.mapDesc(field.descriptor()), field.name()))
                .toList(),
            methods.stream()
                .map(method -> new MemberRename(remapper.map(method.owner()), method.newName(),
                    remapper.mapMethodDesc(method.descriptor()), method.name()))
                .toList()
        );
    }
}
