package org.de.rikr.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The projects opened or saved most recently, newest first, kept in ~/.rikr/recent-projects.txt.
 */
public final class RecentProjects {
    private static final int LIMIT = 8;
    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".rikr", "recent-projects.txt");

    private RecentProjects() {
    }

    /**
     * The recent projects whose files still exist.
     */
    public static List<File> list() {
        return list(FILE);
    }

    public static void add(File project) {
        add(FILE, project);
    }

    static List<File> list(Path file) {
        try {
            if (!Files.exists(file)) {
                return List.of();
            }
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .map(File::new)
                .filter(File::isFile)
                .limit(LIMIT)
                .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    static void add(Path file, File project) {
        List<String> paths = new ArrayList<>();
        paths.add(project.getAbsolutePath());
        list(file).stream()
            .map(File::getAbsolutePath)
            .filter(path -> !path.equals(project.getAbsolutePath()))
            .limit(LIMIT - 1)
            .forEach(paths::add);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, paths, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // A missing recent list only costs a shortcut.
        }
    }
}
