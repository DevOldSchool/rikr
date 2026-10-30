package org.de.rikr.project;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Saves and opens project files. Projects are saved as JSON ({@link ProjectFileJson}); version 1 properties files
 * ({@link LegacyProjectFile}) can still be opened and are converted when saved again.
 */
public class ProjectSessionService {

    public void save(File destination, ProjectSession session) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(session, "session");
        String json = ProjectFileJson.write(session);
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
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            moveAtomically(temporary, destinationPath);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public ProjectSession load(File source) throws IOException {
        Objects.requireNonNull(source, "source");
        String text = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text.stripLeading().startsWith("{") ? ProjectFileJson.read(text) : LegacyProjectFile.read(text);
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
