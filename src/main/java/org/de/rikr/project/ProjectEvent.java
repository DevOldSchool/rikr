package org.de.rikr.project;

import org.de.rikr.rename.RenameStep;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

/**
 * Something that changed in a {@link Project}. The project reacts first, so derived state (indexes, caches,
 * mappings, bookmarks) is consistent before other listeners run.
 */
public sealed interface ProjectEvent {

    /**
     * JARs were loaded, or loose classes were added to {@link org.de.rikr.model.Workspace#LOOSE_CLASSES}.
     */
    record JarsLoaded(List<String> jars) implements ProjectEvent {
        public JarsLoaded {
            jars = List.copyOf(jars);
        }
    }

    record JarRemoved(String jar, List<ClassNode> classes) implements ProjectEvent {
        public JarRemoved {
            classes = List.copyOf(classes);
        }
    }

    record ClassRemoved(String jar, ClassNode classNode) implements ProjectEvent {
    }

    /**
     * Classes of the step's JAR were renamed in place, or a rename step was undone.
     *
     * @param declarationOwners every class whose declaration was renamed
     */
    record Renamed(RenameStep step, boolean undo, Set<String> declarationOwners) implements ProjectEvent {
        public Renamed {
            declarationOwners = Set.copyOf(declarationOwners);
        }
    }

    /**
     * JARs were exported or overwritten, so their unsaved state changed.
     */
    record JarsSaved() implements ProjectEvent {
    }

    record BookmarksChanged() implements ProjectEvent {
    }

    /**
     * A class mapping between two JARs was accepted, rejected or cleared.
     */
    record MappingsChanged() implements ProjectEvent {
    }
}
