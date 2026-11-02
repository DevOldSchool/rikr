package org.de.rikr.decompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceCacheTest {
    @TempDir
    Path directory;

    private final List<String> log = new CopyOnWriteArrayList<>();

    @Test
    void returnsWhatWasWrittenForTheSameBytesOnly() {
        SourceCache cache = new SourceCache(directory, SourceCache.DEFAULT_LIMIT_BYTES, log::add);

        cache.write(bytes("a"), "class a {}\n");

        assertEquals("class a {}\n", cache.read(bytes("a")));
        assertNull(cache.read(bytes("b")));
        // Another run, with the same decompiler, finds it too.
        assertEquals("class a {}\n",
            new SourceCache(directory, SourceCache.DEFAULT_LIMIT_BYTES, log::add).read(bytes("a")));
        assertTrue(log.isEmpty(), log.toString());
    }

    @Test
    void treatsAnUnreadableEntryAsMissing() throws IOException {
        SourceCache cache = new SourceCache(directory, SourceCache.DEFAULT_LIMIT_BYTES, log::add);
        cache.write(bytes("a"), "class a {}\n");
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.writeString(file, "not gzip");
            }
        }

        assertNull(cache.read(bytes("a")));
        assertFalse(log.isEmpty());
    }

    @Test
    void deletesTheEntriesUsedLeastRecentlyWhenOverItsLimit() throws IOException {
        // Each entry is about 250 KB gzipped: three are over the limit, two are under what trimming leaves.
        SourceCache cache = new SourceCache(directory, 700_000, log::add);
        String large = random(400_000);
        cache.write(bytes("old"), large);
        cache.write(bytes("used"), large);
        setLastUsed(directory, 1_000);
        // Reading marks it used now.
        cache.read(bytes("used"));
        cache.write(bytes("new"), large);

        cache.trim();

        assertNull(cache.read(bytes("old")));
        assertTrue(large.equals(cache.read(bytes("used"))));
        assertTrue(large.equals(cache.read(bytes("new"))));
    }

    private static void setLastUsed(Path directory, long millis) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
            }
        }
    }

    // Text gzip can't shrink much, so entries take room on disk.
    private static String random(int length) {
        StringBuilder text = new StringBuilder(length);
        Random random = new Random(42);
        for (int index = 0; index < length; index++) {
            text.append((char) ('a' + random.nextInt(26)));
        }
        return text.toString();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
