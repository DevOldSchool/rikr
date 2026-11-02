package org.de.rikr.ui;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The classes shown in the code view, in order, for Back and Forward. Classes are kept as nodes, so the history
 * survives renames.
 */
final class NavigationHistory {
    private static final int LIMIT = 200;

    private final List<ClassNode> visits = new ArrayList<>();
    private int index = -1;

    /**
     * Record that a class was shown, unless it is the one already current. Visiting after going back drops the
     * forward history, as in a browser.
     */
    void visited(ClassNode classNode) {
        if (index >= 0 && visits.get(index) == classNode) {
            return;
        }
        while (visits.size() > index + 1) {
            visits.removeLast();
        }
        visits.add(classNode);
        if (visits.size() > LIMIT) {
            visits.removeFirst();
        }
        index = visits.size() - 1;
    }

    /**
     * The previous class that is still loaded, or empty at the start of the history.
     */
    Optional<ClassNode> back(Predicate<ClassNode> loaded) {
        for (int candidate = index - 1; candidate >= 0; candidate--) {
            if (loaded.test(visits.get(candidate))) {
                index = candidate;
                return Optional.of(visits.get(candidate));
            }
        }
        return Optional.empty();
    }

    Optional<ClassNode> forward(Predicate<ClassNode> loaded) {
        for (int candidate = index + 1; candidate < visits.size(); candidate++) {
            if (loaded.test(visits.get(candidate))) {
                index = candidate;
                return Optional.of(visits.get(candidate));
            }
        }
        return Optional.empty();
    }

    /**
     * The most recently shown classes, newest first, each once.
     */
    List<ClassNode> recent(int count) {
        LinkedHashSet<ClassNode> recent = new LinkedHashSet<>();
        for (int position = visits.size() - 1; position >= 0 && recent.size() < count; position--) {
            recent.add(visits.get(position));
        }
        return List.copyOf(recent);
    }

    void clear() {
        visits.clear();
        index = -1;
    }
}
