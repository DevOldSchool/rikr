package org.de.rikr.project;

import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameStep;
import org.de.rikr.rename.RenameType;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Reads version 1 project files, which were Java properties files. Projects are no longer saved in this format;
 * opening one and saving it again converts it to JSON.
 */
final class LegacyProjectFile {
    private static final String FORMAT_VERSION = "1";

    private LegacyProjectFile() {
    }

    static ProjectSession read(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        String version = require(properties, "format.version");
        if (!FORMAT_VERSION.equals(version)) {
            throw new IOException("Unsupported project format version: " + version);
        }
        return fromProperties(properties);
    }

    private static ProjectSession fromProperties(Properties properties) throws IOException {
        return new ProjectSession(
            readStrings(properties, "jar"),
            readMappings(properties),
            readRenames(properties),
            readBookmarks(properties),
            readLayout(properties)
        );
    }

    private static List<String> readStrings(Properties properties, String prefix) throws IOException {
        int count = readCount(properties, prefix + ".count");
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(require(properties, prefix + "." + index));
        }
        return values;
    }

    private static List<ClassMappingDecision> readMappings(Properties properties) throws IOException {
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

    private static List<RenameStep> readRenames(Properties properties) throws IOException {
        int count = readCount(properties, "rename.count");
        List<RenameStep> renames = new ArrayList<>(count);
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

    private static List<ProjectBookmark> readBookmarks(Properties properties) throws IOException {
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

    private static ProjectLayout readLayout(Properties properties) throws IOException {
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

    private static int readCount(Properties properties, String key) throws IOException {
        int count = readInteger(properties, key);
        if (count < 0) {
            throw new IOException("Negative entry count for " + key);
        }
        return count;
    }

    private static int readInteger(Properties properties, String key) throws IOException {
        String value = require(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid integer for " + key + ": " + value, exception);
        }
    }

    private static String require(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IOException("Missing project property: " + key);
        }
        return value;
    }

    private static <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type) throws IOException {
        String value = require(properties, key);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid value for " + key + ": " + value, exception);
        }
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
