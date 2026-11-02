package org.de.rikr.theme;

import com.formdev.flatlaf.util.SystemInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Whether the operating system is in its dark appearance. On macOS this reads the user default the system sets;
 * elsewhere there is no portable answer, so the light theme is assumed.
 */
public final class SystemAppearance {
    private static volatile BooleanSupplier detector = SystemAppearance::detect;

    private SystemAppearance() {
    }

    public static boolean isDark() {
        return detector.getAsBoolean();
    }

    /**
     * Replace the detection, for tests and for platforms with their own way of knowing.
     */
    public static void setDetector(BooleanSupplier replacement) {
        detector = replacement == null ? SystemAppearance::detect : replacement;
    }

    private static boolean detect() {
        if (!SystemInfo.isMacOS) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                .redirectErrorStream(true).start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            // The key exists only while the appearance is dark; in light mode the command reports it missing.
            return process.exitValue() == 0 && output.trim().equalsIgnoreCase("Dark");
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
