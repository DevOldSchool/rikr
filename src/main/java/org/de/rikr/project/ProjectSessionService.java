package org.de.rikr.project;


import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class ProjectSessionService {
    private static final String FORMAT_VERSION = "1";

    public void save(File destination, ProjectSession session) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(session, "session");
        Properties properties = toProperties(session);
        Path destinationPath = destination.toPath().toAbsolutePath();
        Path parent = destinationPath.getParent();
        if (parent == null) {
            throw new IOException("Project destination has no parent directory");
        }
        Files.createDirectories(parent);
        String prefix = destination.getName();
        if (prefix.length() < 3) {
            prefix = "rikr-" + prefix;
        }
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Rikr project session");
            }
            moveAtomically(temporary, destinationPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public ProjectSession load(File source) throws IOException {
        Objects.requireNonNull(source, "source");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(source.toPath())) {
            properties.load(input);
        }
        String version = require(properties, "format.version");
        if (!FORMAT_VERSION.equals(version)) {
            throw new IOException("Unsupported project format version: " + version);
        }
        return fromProperties(properties);
    }

    private Properties toProperties(ProjectSession session) {
        Properties properties = new Properties();
        properties.setProperty("format.version", FORMAT_VERSION);
        writeStrings(properties, "jar", session.jarPaths());
        writeMappings(properties, session.mappings());
        writeRenames(properties, session.renames());
        writeBookmarks(properties, session.bookmarks());
        writeLayout(properties, session.layout());
        return properties;
    }

    private ProjectSession fromProperties(Properties properties) throws IOException {
        return new ProjectSession(
            readStrings(properties, "jar"),
            readMappings(properties),
            readRenames(properties),
            readBookmarks(properties),
            readLayout(properties)
        );
    }

    private void writeStrings(Properties properties, String prefix, List<String> values) {
        properties.setProperty(prefix + ".count", Integer.toString(values.size()));
        for (int index = 0; index < values.size(); index++) {
            properties.setProperty(prefix + "." + index, values.get(index));
        }
    }

    private List<String> readStrings(Properties properties, String prefix) throws IOException {
        int count = readCount(properties, prefix + ".count");
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(require(properties, prefix + "." + index));
        }
        return values;
    }

    private void writeMappings(Properties properties, List<ClassMappingDecision> mappings) {
        properties.setProperty("mapping.count", Integer.toString(mappings.size()));
        for (int index = 0; index < mappings.size(); index++) {
            String prefix = "mapping." + index + ".";
            ClassMappingDecision mapping = mappings.get(index);
            properties.setProperty(prefix + "sourceJar", mapping.sourceJar());
            properties.setProperty(prefix + "targetJar", mapping.targetJar());
            properties.setProperty(prefix + "sourceClass", mapping.sourceClassName());
            properties.setProperty(prefix + "targetClass", mapping.targetClassName());
            properties.setProperty(prefix + "similarity", Integer.toString(mapping.similarityPercent()));
        }
    }

    private List<ClassMappingDecision> readMappings(Properties properties) throws IOException {
        int count = readCount(properties, "mapping.count");
        List<ClassMappingDecision> mappings = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "mapping." + index + ".";
            mappings.add(new ClassMappingDecision(
                require(properties, prefix + "sourceJar"),
                require(properties, prefix + "targetJar"),
                require(properties, prefix + "sourceClass"),
                require(properties, prefix + "targetClass"),
                readInteger(properties, prefix + "similarity")
            ));
        }
        return mappings;
    }

    private void writeRenames(Properties properties, List<RenameOperation> renames) {
        properties.setProperty("rename.count", Integer.toString(renames.size()));
        for (int index = 0; index < renames.size(); index++) {
            String prefix = "rename." + index + ".";
            RenameOperation operation = renames.get(index);
            properties.setProperty(prefix + "type", operation.type().name());
            properties.setProperty(prefix + "jar", operation.jarName());
            properties.setProperty(prefix + "owner", operation.ownerName());
            properties.setProperty(prefix + "descriptor", nullToEmpty(operation.descriptor()));
            properties.setProperty(prefix + "oldName", operation.oldName());
            properties.setProperty(prefix + "newName", operation.newName());
        }
    }

    private List<RenameOperation> readRenames(Properties properties) throws IOException {
        int count = readCount(properties, "rename.count");
        List<RenameOperation> renames = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "rename." + index + ".";
            renames.add(new RenameOperation(
                readEnum(properties, prefix + "type", RenameType.class),
                require(properties, prefix + "jar"),
                require(properties, prefix + "owner"),
                emptyToNull(require(properties, prefix + "descriptor")),
                require(properties, prefix + "oldName"),
                require(properties, prefix + "newName")
            ));
        }
        return renames;
    }

    private void writeBookmarks(Properties properties, List<ProjectBookmark> bookmarks) {
        properties.setProperty("bookmark.count", Integer.toString(bookmarks.size()));
        for (int index = 0; index < bookmarks.size(); index++) {
            String prefix = "bookmark." + index + ".";
            ProjectBookmark bookmark = bookmarks.get(index);
            properties.setProperty(prefix + "type", bookmark.type().name());
            properties.setProperty(prefix + "jar", bookmark.jarName());
            properties.setProperty(prefix + "class", bookmark.className());
            properties.setProperty(prefix + "member", nullToEmpty(bookmark.memberName()));
            properties.setProperty(prefix + "descriptor", nullToEmpty(bookmark.descriptor()));
        }
    }

    private List<ProjectBookmark> readBookmarks(Properties properties) throws IOException {
        int count = readCount(properties, "bookmark.count");
        List<ProjectBookmark> bookmarks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "bookmark." + index + ".";
            bookmarks.add(new ProjectBookmark(
                readEnum(properties, prefix + "type", ProjectBookmarkType.class),
                require(properties, prefix + "jar"),
                require(properties, prefix + "class"),
                emptyToNull(require(properties, prefix + "member")),
                emptyToNull(require(properties, prefix + "descriptor"))
            ));
        }
        return bookmarks;
    }

    private void writeLayout(Properties properties, ProjectLayout layout) {
        properties.setProperty("layout.x", Integer.toString(layout.windowX()));
        properties.setProperty("layout.y", Integer.toString(layout.windowY()));
        properties.setProperty("layout.width", Integer.toString(layout.windowWidth()));
        properties.setProperty("layout.height", Integer.toString(layout.windowHeight()));
        properties.setProperty("layout.mainDivider", Integer.toString(layout.mainDivider()));
        properties.setProperty("layout.verticalDivider", Integer.toString(layout.verticalDivider()));
        properties.setProperty("layout.contentDivider", Integer.toString(layout.contentDivider()));
        properties.setProperty("layout.logVisible", Boolean.toString(layout.logVisible()));
        properties.setProperty("layout.simulatorVisible", Boolean.toString(layout.simulatorVisible()));
        properties.setProperty("layout.splitJavaVisible", Boolean.toString(layout.splitJavaVisible()));
        properties.setProperty("layout.workspace", layout.selectedWorkspace());
    }

    private ProjectLayout readLayout(Properties properties) throws IOException {
        return new ProjectLayout(
            readInteger(properties, "layout.x"),
            readInteger(properties, "layout.y"),
            readInteger(properties, "layout.width"),
            readInteger(properties, "layout.height"),
            readInteger(properties, "layout.mainDivider"),
            readInteger(properties, "layout.verticalDivider"),
            readInteger(properties, "layout.contentDivider"),
            Boolean.parseBoolean(require(properties, "layout.logVisible")),
            Boolean.parseBoolean(require(properties, "layout.simulatorVisible")),
            Boolean.parseBoolean(require(properties, "layout.splitJavaVisible")),
            require(properties, "layout.workspace")
        );
    }

    private int readCount(Properties properties, String key) throws IOException {
        int count = readInteger(properties, key);
        if (count < 0) {
            throw new IOException("Negative entry count for " + key);
        }
        return count;
    }

    private int readInteger(Properties properties, String key) throws IOException {
        String value = require(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer for " + key + ": " + value, exception);
        }
    }

    private String require(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IOException("Missing project property: " + key);
        }
        return value;
    }

    private <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type) throws IOException {
        String value = require(properties, key);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid value for " + key + ": " + value, exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
