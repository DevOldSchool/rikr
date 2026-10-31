package org.de.rikr.decompiler;

import org.de.rikr.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decompiles classes in a JVM of its own, {@link DecompilerWorker}, started for the first class and kept for the
 * next. FernFlower can take minutes and gigabytes on obfuscated code; in its own process it can't fill the
 * application's heap or slow it with garbage collection, and a class that takes too long is stopped by ending the
 * process. When there is no {@code java} launcher to start, as in a runtime built without one, classes are
 * decompiled in this JVM instead.
 * <p>
 * One class is decompiled at a time; callers use a single thread.
 */
final class WorkerDecompiler implements AutoCloseable {
    /** The worker's heap: the largest classes of game clients need under 1 GB. */
    static final String HEAP = "-Xmx1g";
    /** How long one class may take; the largest classes of game clients take under a minute. */
    static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final Logger logger;
    private final List<String> command;
    private final Duration timeout;
    private volatile Process process;
    private DataOutputStream requests;
    private DataInputStream replies;
    private boolean inThisJvm;

    WorkerDecompiler(Logger logger) {
        this(logger, command(), TIMEOUT);
    }

    /**
     * @param command the command that starts {@link DecompilerWorker}, or null to decompile in this JVM
     */
    WorkerDecompiler(Logger logger, List<String> command, Duration timeout) {
        this.logger = logger;
        this.command = command;
        this.timeout = timeout;
        this.inThisJvm = command == null;
    }

    /**
     * @return the Java, with FernFlower's original line comments, or why there is none
     */
    synchronized DecompileResult decompile(String internalName, byte[] classBytes) {
        if (!inThisJvm && !started()) {
            inThisJvm = true;
        }
        if (inThisJvm) {
            return result(new JetBrainsDecompilerService(logger).decompile(internalName, classBytes));
        }
        Process worker = process;
        AtomicBoolean timedOut = new AtomicBoolean();
        CompletableFuture<Void> watchdog = CompletableFuture.runAsync(() -> {
            timedOut.set(true);
            worker.destroyForcibly();
        }, CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS));
        try {
            DecompilerWorker.writeString(requests, internalName);
            DecompilerWorker.writeBytes(requests, classBytes);
            requests.flush();
            String source = replies.readBoolean() ? DecompilerWorker.readString(replies) : null;
            int messages = replies.readInt();
            for (int index = 0; index < messages; index++) {
                logger.log(DecompilerWorker.readString(replies));
            }
            return result(source);
        } catch (IOException stopped) {
            stop();
            if (timedOut.get()) {
                return DecompileResult.failure("Decompiling " + internalName + " took longer than "
                    + timeout.toSeconds() + " seconds, so it was stopped.");
            }
            logger.log("[Decompiler] The decompiler process stopped: " + stopped.getMessage());
            return DecompileResult.failure("The decompiler stopped while decompiling " + internalName + ".");
        } finally {
            watchdog.cancel(false);
        }
    }

    private static DecompileResult result(String source) {
        return source == null || source.isEmpty()
            ? DecompileResult.failure("Failed to decompile using JetBrains decompiler.")
            : DecompileResult.success(source);
    }

    // Starts the worker unless it is running; false if it can't be started.
    private boolean started() {
        if (process != null && process.isAlive()) {
            return true;
        }
        try {
            Process started = new ProcessBuilder(command).redirectError(Redirect.INHERIT).start();
            requests = new DataOutputStream(new BufferedOutputStream(started.getOutputStream()));
            replies = new DataInputStream(new BufferedInputStream(started.getInputStream()));
            process = started;
            return true;
        } catch (IOException exception) {
            logger.log("[Decompiler] Can't start a decompiler process, so classes are decompiled in Rikr: "
                + exception.getMessage());
            return false;
        }
    }

    private void stop() {
        Process running = process;
        process = null;
        if (running != null) {
            running.destroyForcibly();
        }
    }

    /**
     * The command that starts the worker with this JVM's launcher and class path, or null when the runtime has no
     * launcher.
     */
    static List<String> command() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        if (!Files.isExecutable(java)) {
            return null;
        }
        return List.of(java.toString(), HEAP, "-Djava.awt.headless=true", "-cp",
            System.getProperty("java.class.path"), DecompilerWorker.class.getName());
    }

    @Override
    public void close() {
        // Not synchronized: a class may be decompiling, and ending the process ends that too.
        stop();
    }
}
