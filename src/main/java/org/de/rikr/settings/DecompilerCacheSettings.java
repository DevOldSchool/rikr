package org.de.rikr.settings;

import org.de.rikr.decompiler.DecompilerCacheService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Where decompiled Java is kept between runs, and how much of it (docs/decisions/0013). Kept with the other settings,
 * but readable without them, so the MCP server uses the same cache as the GUI without starting Swing.
 *
 * @param directory the cache's folder, or blank for the default one
 * @param maxSizeMb how large the cache may grow, in megabytes, before the entries used least recently are deleted
 */
public record DecompilerCacheSettings(String directory, int maxSizeMb) {
    private static final long MEGABYTE = 1024L * 1024L;
    public static final int DEFAULT_MAX_SIZE_MB =
        (int) (DecompilerCacheService.DEFAULT_DISK_CACHE_BYTES / MEGABYTE);
    public static final int MIN_MAX_SIZE_MB = 50;
    public static final int MAX_MAX_SIZE_MB = 100_000;
    /** The folder used when none is set. */
    public static final Path DEFAULT_DIRECTORY = DecompilerCacheService.DEFAULT_DISK_CACHE_DIRECTORY;

    static final String KEY_DIRECTORY = "decompiler.cache.directory";
    static final String KEY_MAX_SIZE_MB = "decompiler.cache.maxSizeMb";

    public DecompilerCacheSettings {
        directory = directory == null ? "" : directory.strip();
        maxSizeMb = Math.clamp(maxSizeMb, MIN_MAX_SIZE_MB, MAX_MAX_SIZE_MB);
    }

    public static DecompilerCacheSettings defaults() {
        return new DecompilerCacheSettings("", DEFAULT_MAX_SIZE_MB);
    }

    /** The settings saved in Rikr's settings file, or the defaults. */
    public static DecompilerCacheSettings load() {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(Settings.Store.CONFIG_FILE)) {
            properties.load(in);
        } catch (IOException missingOrUnreadable) {
            return defaults();
        }
        return fromProperties(properties);
    }

    /** The folder to use: the one set, or the default. */
    public Path directoryPath() {
        return directory.isEmpty() ? DEFAULT_DIRECTORY : Paths.get(directory);
    }

    public long maxSizeBytes() {
        return maxSizeMb * MEGABYTE;
    }

    static DecompilerCacheSettings fromProperties(Properties properties) {
        int maxSizeMb = DEFAULT_MAX_SIZE_MB;
        try {
            maxSizeMb = Integer.parseInt(properties.getProperty(KEY_MAX_SIZE_MB, Integer.toString(maxSizeMb)).strip());
        } catch (NumberFormatException ignored) {
            // The default, as for any setting that can't be read.
        }
        return new DecompilerCacheSettings(properties.getProperty(KEY_DIRECTORY, ""), maxSizeMb);
    }

    void writeTo(Properties properties) {
        properties.setProperty(KEY_DIRECTORY, directory);
        properties.setProperty(KEY_MAX_SIZE_MB, Integer.toString(maxSizeMb));
    }
}
