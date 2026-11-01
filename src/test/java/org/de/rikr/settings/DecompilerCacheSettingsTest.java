package org.de.rikr.settings;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecompilerCacheSettingsTest {
    @Test
    void savesAndLoadsTheFolderAndSize() {
        Properties properties = new Properties();
        new DecompilerCacheSettings(" /tmp/rikr-cache ", 2_000).writeTo(properties);

        DecompilerCacheSettings loaded = DecompilerCacheSettings.fromProperties(properties);

        assertEquals("/tmp/rikr-cache", loaded.directory());
        assertEquals(Path.of("/tmp/rikr-cache"), loaded.directoryPath());
        assertEquals(2_000L * 1024 * 1024, loaded.maxSizeBytes());
    }

    @Test
    void usesTheDefaultsForMissingOrUnreadableValues() {
        Properties properties = new Properties();
        properties.setProperty(DecompilerCacheSettings.KEY_MAX_SIZE_MB, "lots");

        DecompilerCacheSettings loaded = DecompilerCacheSettings.fromProperties(properties);

        assertEquals(DecompilerCacheSettings.defaults(), loaded);
        assertEquals(DecompilerCacheSettings.DEFAULT_DIRECTORY, loaded.directoryPath());
    }

    @Test
    void keepsTheSizeWithinItsLimits() {
        assertEquals(DecompilerCacheSettings.MIN_MAX_SIZE_MB, new DecompilerCacheSettings("", 1).maxSizeMb());
        assertEquals(DecompilerCacheSettings.MAX_MAX_SIZE_MB, new DecompilerCacheSettings("", 1 << 30).maxSizeMb());
    }
}
