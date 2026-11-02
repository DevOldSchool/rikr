package org.de.rikr.decompiler;

import org.de.rikr.Logger;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Decompiled Java kept on disk between runs, by the class's bytes: a class opened again, in this project or another,
 * after a restart, is shown at once instead of being decompiled again. A renamed class has other bytes, so it is
 * decompiled again and nothing needs to be invalidated. The key also covers the decompiler's own classes, so a new
 * FernFlower, a changed patch or changed options start afresh.
 * <p>
 * Entries are gzipped Java, written to a temporary file that is moved into place, so a Rikr that stops or another
 * one reading at the same time never sees half an entry. When the cache grows past its limit, the entries used least
 * recently are deleted. Anything that goes wrong reading or writing is a miss, never a failure.
 * <p>
 * Used from one thread at a time.
 */
final class SourceCache {
    /** Overrides where the cache is, for tests and for anyone who wants it elsewhere. */
    static final String DIRECTORY_PROPERTY = "rikr.cache.dir";
    /** The largest class of a game client is a few megabytes gzipped; this keeps hundreds of them. */
    static final long DEFAULT_LIMIT_BYTES = 500L * 1024 * 1024;
    /** The folder used when none is chosen. */
    static final Path DEFAULT_DIRECTORY = Paths.get(System.getProperty("user.home"), ".rikr", "cache", "decompiled");
    // Change it when what is stored changes, such as the options the decompiler runs with.
    private static final String FORMAT = "1";
    // Entries written since the cache's size was last checked, before it is checked again.
    private static final long CHECK_AFTER_BYTES = 50L * 1024 * 1024;

    private final Path directory;
    private final long limitBytes;
    private final Logger logger;
    private final byte[] salt;
    // Starts high, so the first entry written checks the size of what earlier runs left.
    private long writtenSinceCheck = Long.MAX_VALUE / 2;

    SourceCache(Path directory, long limitBytes, Logger logger) {
        this.directory = directory;
        this.limitBytes = limitBytes;
        this.logger = logger;
        this.salt = (FORMAT + "\n" + fingerprint(Fernflower.class) + "\n" + fingerprint(ClassWrapper.class) + "\n"
            + fingerprint(JetBrainsDecompilerService.class)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The cache in this folder, unless {@value #DIRECTORY_PROPERTY} names another.
     */
    static SourceCache in(Path directory, long limitBytes, Logger logger) {
        String property = System.getProperty(DIRECTORY_PROPERTY);
        Path chosen = property != null && !property.isBlank() ? Paths.get(property) : directory;
        return new SourceCache(chosen, limitBytes, logger);
    }

    /**
     * @return the Java decompiled from these class bytes before, or null
     */
    String read(byte[] classBytes) {
        Path file = file(classBytes);
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // When it was last used decides what is deleted first.
            Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
            return source;
        } catch (NoSuchFileException missing) {
            return null;
        } catch (IOException | RuntimeException unreadable) {
            logger.log("[Decompiler] Can't read the cached Java in " + file + ": " + unreadable.getMessage());
            return null;
        }
    }

    void write(byte[] classBytes, String source) {
        Path file = file(classBytes);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(temporary))) {
                out.write(source.getBytes(StandardCharsets.UTF_8));
            }
            long size = Files.size(temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
            writtenSinceCheck += size;
            if (writtenSinceCheck >= CHECK_AFTER_BYTES) {
                writtenSinceCheck = 0;
                trim();
            }
        } catch (IOException | RuntimeException unwritable) {
            logger.log("[Decompiler] Can't cache the Java in " + file + ": " + unwritable.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Left for the next trim.
                }
            }
        }
    }

    /**
     * Deletes the entries used least recently, and temporary files left behind, until the cache is under a
     * fifth below its limit.
     */
    void trim() throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        record Entry(Path path, long size, long lastUsed) {
        }
        List<Entry> entries = new ArrayList<>();
        long total = 0;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                try {
                    Entry entry = new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toMillis());
                    entries.add(entry);
                    total += entry.size();
                } catch (NoSuchFileException gone) {
                    // Another Rikr deleted it.
                }
            }
        }
        if (total <= limitBytes) {
            return;
        }
        entries.sort(Comparator.comparingLong(Entry::lastUsed));
        long target = limitBytes - limitBytes / 5;
        for (Entry entry : entries) {
            if (total <= target) {
                break;
            }
            Files.deleteIfExists(entry.path());
            total -= entry.size();
        }
    }

    Path directory() {
        return directory;
    }

    private Path file(byte[] classBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Every Java runtime has SHA-256", impossible);
        }
        digest.update(salt);
        String key = HexFormat.of().formatHex(digest.digest(classBytes));
        return directory.resolve(key.substring(0, 2)).resolve(key + ".java.gz");
    }

    // Where the class was loaded from, and its size and time there, which change with each build of it.
    private static String fingerprint(Class<?> type) {
        URL resource = type.getResource(type.getSimpleName() + ".class");
        if (resource == null) {
            return type.getName();
        }
        try {
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            try (InputStream ignored = connection.getInputStream()) {
                return resource + " " + connection.getContentLengthLong() + " " + connection.getLastModified();
            }
        } catch (IOException unreadable) {
            return resource.toString();
        }
    }
}
