package org.de.rikr.project;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Delivers project events synchronously, on the publishing thread, in subscription order.
 */
public class EventBus {
    private final List<Consumer<ProjectEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * @return removes the listener again
     */
    public Runnable subscribe(Consumer<ProjectEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void publish(ProjectEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<ProjectEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
