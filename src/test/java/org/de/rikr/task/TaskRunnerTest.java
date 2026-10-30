package org.de.rikr.task;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunnerTest {
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final ExecutorService eventThread = Executors.newSingleThreadExecutor(
        runnable -> new Thread(runnable, "test-event-thread")
    );
    private final TaskRunner tasks = new TaskRunner(log::add, eventThread);

    @AfterEach
    void close() {
        tasks.close();
        eventThread.shutdownNow();
    }

    @Test
    void runsWorkInTheBackgroundAndDeliversTheResultOnTheEventThread() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        tasks.run("Work", () -> Thread.currentThread().getName(), worker -> {
            seen.add(worker);
            seen.add(Thread.currentThread().getName());
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("rikr-task", "test-event-thread"), seen);
    }

    @Test
    void runsTasksInTheOrderTheyWereSubmitted() throws Exception {
        List<Integer> order = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            int task = i;
            tasks.run("Task " + task, () -> {
                Thread.sleep(30L * (3 - task));
                return task;
            }, result -> {
                order.add(result);
                done.countDown();
            });
        }

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(0, 1, 2), order);
    }

    @Test
    void reportsFailuresOfTheWorkAndOfTheResultHandler() throws Exception {
        List<String> failures = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(2);

        tasks.run("Read", () -> {
            throw new IOException("missing file");
        }, result -> {
        }, exception -> {
            failures.add(exception.getMessage());
            done.countDown();
        });
        tasks.run("Apply", () -> "value", result -> {
            throw new IllegalStateException("bad " + result);
        }, exception -> {
            failures.add(exception.getMessage());
            done.countDown();
        });

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("missing file", "bad value"), failures);
    }

    @Test
    void logsFailuresWhenNoHandlerIsGiven() throws Exception {
        CountDownLatch done = new CountDownLatch(1);

        tasks.run("Loading files", () -> {
            throw new IOException("disk error");
        }, result -> {
        });
        tasks.run("Next", () -> null, result -> done.countDown());

        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("Loading files failed: disk error"), log);
    }
}
