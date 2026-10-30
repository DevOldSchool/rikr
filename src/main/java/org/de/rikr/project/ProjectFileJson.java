package org.de.rikr.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.de.rikr.bookmark.ProjectBookmark;
import org.de.rikr.bookmark.ProjectBookmarkType;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenameType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The JSON project file. The file has its own records, separate from the domain types, so renaming a field in the
 * code cannot silently change the format; {@link #FORMAT_VERSION} changes whenever the format does.
 */
final class ProjectFileJson {
    static final int FORMAT_VERSION = 2;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private ProjectFileJson() {
    }

    record FileModel(
        int formatVersion,
        List<String> jars,
        List<RenameModel> renames,
        List<MappingModel> mappings,
        List<BookmarkModel> bookmarks,
        LayoutModel layout
    ) {
    }

    record RenameModel(String type, String jar, String owner, String descriptor, String oldName, String newName) {
    }

    record MappingModel(String sourceJar, String targetJar, String sourceClass, String targetClass, int similarityPercent) {
    }

    record BookmarkModel(String type, String jar, String className, String member, String descriptor) {
    }

    record LayoutModel(
        int x,
        int y,
        int width,
        int height,
        int mainDivider,
        int verticalDivider,
        int contentDivider,
        boolean logVisible,
        boolean simulatorVisible,
        boolean javaVisible,
        String workspace
    ) {
    }

    static String write(ProjectSession session) {
        ProjectLayout layout = session.layout();
        FileModel file = new FileModel(
            FORMAT_VERSION,
            session.jarPaths(),
            session.renames().stream()
                .map(rename -> new RenameModel(rename.type().name(), rename.jarName(), rename.ownerName(),
                    rename.descriptor(), rename.oldName(), rename.newName()))
                .toList(),
            session.mappings().stream()
                .map(mapping -> new MappingModel(mapping.sourceJar(), mapping.targetJar(), mapping.sourceClassName(),
                    mapping.targetClassName(), mapping.similarityPercent()))
                .toList(),
            session.bookmarks().stream()
                .map(bookmark -> new BookmarkModel(bookmark.type().name(), bookmark.jarName(), bookmark.className(),
                    bookmark.memberName(), bookmark.descriptor()))
                .toList(),
            new LayoutModel(layout.windowX(), layout.windowY(), layout.windowWidth(), layout.windowHeight(),
                layout.mainDivider(), layout.verticalDivider(), layout.contentDivider(), layout.logVisible(),
                layout.simulatorVisible(), layout.splitJavaVisible(), layout.selectedWorkspace())
        );
        return GSON.toJson(file) + "\n";
    }

    static ProjectSession read(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement version = root.get("formatVersion");
            if (version == null || !version.isJsonPrimitive()) {
                throw new IOException("Project file has no formatVersion");
            }
            int formatVersion = version.getAsInt();
            if (formatVersion > FORMAT_VERSION) {
                throw new IOException("Project file version " + formatVersion
                    + " was written by a newer version of Rikr; this version reads up to " + FORMAT_VERSION);
            }
            if (formatVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported project format version: " + formatVersion);
            }
            return toSession(GSON.fromJson(root, FileModel.class));
        } catch (JsonParseException | IllegalStateException | NumberFormatException exception) {
            throw new IOException("Project file is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    private static ProjectSession toSession(FileModel file) throws IOException {
        List<String> jars = new ArrayList<>();
        List<String> jarPaths = require(file.jars(), "jars");
        for (int index = 0; index < jarPaths.size(); index++) {
            jars.add(require(jarPaths.get(index), "jars[" + index + "]"));
        }

        List<RenameOperation> renames = new ArrayList<>();
        List<RenameModel> renameModels = require(file.renames(), "renames");
        for (int index = 0; index < renameModels.size(); index++) {
            String path = "renames[" + index + "]";
            RenameModel rename = require(renameModels.get(index), path);
            renames.add(new RenameOperation(
                parseEnum(rename.type(), RenameType::valueOf, path + ".type"),
                require(rename.jar(), path + ".jar"),
                require(rename.owner(), path + ".owner"),
                rename.descriptor(),
                require(rename.oldName(), path + ".oldName"),
                require(rename.newName(), path + ".newName")
            ));
        }

        List<ClassMappingDecision> mappings = new ArrayList<>();
        List<MappingModel> mappingModels = require(file.mappings(), "mappings");
        for (int index = 0; index < mappingModels.size(); index++) {
            String path = "mappings[" + index + "]";
            MappingModel mapping = require(mappingModels.get(index), path);
            mappings.add(new ClassMappingDecision(
                require(mapping.sourceJar(), path + ".sourceJar"),
                require(mapping.targetJar(), path + ".targetJar"),
                require(mapping.sourceClass(), path + ".sourceClass"),
                require(mapping.targetClass(), path + ".targetClass"),
                mapping.similarityPercent()
            ));
        }

        List<ProjectBookmark> bookmarks = new ArrayList<>();
        List<BookmarkModel> bookmarkModels = require(file.bookmarks(), "bookmarks");
        for (int index = 0; index < bookmarkModels.size(); index++) {
            String path = "bookmarks[" + index + "]";
            BookmarkModel bookmark = require(bookmarkModels.get(index), path);
            bookmarks.add(new ProjectBookmark(
                parseEnum(bookmark.type(), ProjectBookmarkType::valueOf, path + ".type"),
                require(bookmark.jar(), path + ".jar"),
                require(bookmark.className(), path + ".className"),
                bookmark.member(),
                bookmark.descriptor()
            ));
        }

        LayoutModel layout = require(file.layout(), "layout");
        return new ProjectSession(jars, mappings, renames, bookmarks, new ProjectLayout(
            layout.x(),
            layout.y(),
            layout.width(),
            layout.height(),
            layout.mainDivider(),
            layout.verticalDivider(),
            layout.contentDivider(),
            layout.logVisible(),
            layout.simulatorVisible(),
            layout.javaVisible(),
            require(layout.workspace(), "layout.workspace")
        ));
    }

    private static <T> T require(T value, String path) throws IOException {
        if (value == null) {
            throw new IOException("Project file is missing " + path);
        }
        return value;
    }

    private static <T> T parseEnum(String value, Function<String, T> parse, String path) throws IOException {
        try {
            return parse.apply(require(value, path));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Project file has an unknown " + path + ": " + value, exception);
        }
    }
}
