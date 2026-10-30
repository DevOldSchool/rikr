package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentProjectsTest {
    @TempDir
    Path directory;

    @Test
    void keepsNewestFirstWithoutDuplicatesOrMissingFiles() throws IOException {
        Path list = directory.resolve("settings").resolve("recent-projects.txt");
        File first = Files.createFile(directory.resolve("first.rikr")).toFile();
        File second = Files.createFile(directory.resolve("second.rikr")).toFile();
        File deleted = Files.createFile(directory.resolve("deleted.rikr")).toFile();

        RecentProjects.add(list, first);
        RecentProjects.add(list, deleted);
        RecentProjects.add(list, second);
        RecentProjects.add(list, first);
        Files.delete(deleted.toPath());

        assertEquals(List.of(first.getAbsoluteFile(), second.getAbsoluteFile()), RecentProjects.list(list));
    }

    @Test
    void keepsAtMostEight() throws IOException {
        Path list = directory.resolve("recent-projects.txt");
        for (int number = 0; number < 10; number++) {
            RecentProjects.add(list, Files.createFile(directory.resolve(number + ".rikr")).toFile());
        }

        List<File> recent = RecentProjects.list(list);
        assertEquals(8, recent.size());
        assertEquals(directory.resolve("9.rikr").toFile().getAbsoluteFile(), recent.getFirst());
    }

    @Test
    void anUnreadableListIsEmpty() {
        assertEquals(List.of(), RecentProjects.list(directory.resolve("missing.txt")));
    }
}
