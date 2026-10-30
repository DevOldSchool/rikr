package org.de.rikr.task;

import org.de.rikr.Logger;

import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow work, such as reading JARs, on a background thread and hands the result back on the Swing event
 * thread. Tasks run one at a time, in the order they were submitted.
 * <p>
 * Background work must not change the project. It returns a result, and the event thread applies it, so the
 * project only ever changes on one thread.
 */
public class TaskRunner implements AutoCloseable {
    private final Logger logger;
    private final Executor eventThread;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rikr-task");
        thread.setDaemon(true);
        return thread;
    });

    public TaskRunner(Logger logger) {
        this(logger, SwingUtilities::invokeLater);
    }

    TaskRunner(Logger logger, Executor eventThread) {
        this.logger = logger;
        this.eventThread = eventThread;
    }

    /**
     * Run the task and log its failure.
     */
    public <T> void run(String name, Callable<T> work, Consumer<T> onSuccess) {
        run(name, work, onSuccess, exception -> logger.log(name + " failed: " + exception.getMessage()));
    }

    /**
     * @param onSuccess called on the event thread with the task's result
     * @param onFailure called on the event thread if the task or {@code onSuccess} throws
     */
    public <T> void run(String name, Callable<T> work, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        worker.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Exception exception) {
                eventThread.execute(() -> onFailure.accept(exception));
                return;
            }
            eventThread.execute(() -> {
                try {
                    onSuccess.accept(result);
                } catch (RuntimeException exception) {
                    onFailure.accept(exception);
                }
            });
        });
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }
}
