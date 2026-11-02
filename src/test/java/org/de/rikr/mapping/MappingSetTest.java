package org.de.rikr.mapping;

import org.de.rikr.model.ClassRef;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingSetTest {
    private final MappingSet mappings = new MappingSet();

    @Test
    void classRenameKeepsAcceptedMappingsOnBothSides() {
        mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "a", "x", 90));
        mappings.accept(new ClassMappingDecision("new.jar", "old.jar", "x", "a", 90));

        mappings.renameClass(new ClassRef("old.jar", "a"), "Player");

        assertEquals(
            Optional.of(new ClassMappingDecision("old.jar", "new.jar", "Player", "x", 90)),
            mappings.accepted(new ClassRef("old.jar", "Player"), "new.jar")
        );
        assertEquals(
            Optional.of(new ClassMappingDecision("new.jar", "old.jar", "x", "Player", 90)),
            mappings.accepted(new ClassRef("new.jar", "x"), "old.jar")
        );
        assertTrue(mappings.accepted(new ClassRef("old.jar", "a"), "new.jar").isEmpty());
    }

    @Test
    void classRenameOnlyAffectsItsOwnJar() {
        mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "a", "a", 100));

        mappings.renameClass(new ClassRef("new.jar", "a"), "Player");

        assertEquals(
            List.of(new ClassMappingDecision("old.jar", "new.jar", "a", "Player", 100)),
            mappings.allAccepted()
        );
    }

    @Test
    void classRenameKeepsRejections() {
        mappings.reject(new ClassRef("old.jar", "a"), new ClassRef("new.jar", "x"));

        mappings.renameClass(new ClassRef("new.jar", "x"), "Npc");

        assertTrue(mappings.isRejected(new ClassRef("old.jar", "a"), new ClassRef("new.jar", "Npc")));
        assertFalse(mappings.isRejected(new ClassRef("old.jar", "a"), new ClassRef("new.jar", "x")));
    }

    @Test
    void targetCanOnlyBeClaimedOnce() {
        mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "a", "x", 90));

        assertThrows(
            IllegalArgumentException.class,
            () -> mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "b", "x", 90))
        );
    }

    @Test
    void acceptingClearsTheRejectionAndRejectingRemovesTheMapping() {
        ClassRef source = new ClassRef("old.jar", "a");
        ClassRef target = new ClassRef("new.jar", "x");
        mappings.reject(source, target);

        mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "a", "x", 90));
        assertFalse(mappings.isRejected(source, target));

        mappings.reject(source, target);
        assertTrue(mappings.accepted(source, "new.jar").isEmpty());
    }

    @Test
    void removingAClassDropsEveryDecisionThatNamesIt() {
        mappings.accept(new ClassMappingDecision("old.jar", "new.jar", "a", "x", 90));
        mappings.reject(new ClassRef("old.jar", "b"), new ClassRef("new.jar", "x"));

        mappings.removeClass(new ClassRef("new.jar", "x"));

        assertTrue(mappings.allAccepted().isEmpty());
        assertFalse(mappings.isRejected(new ClassRef("old.jar", "b"), new ClassRef("new.jar", "x")));
    }
}
