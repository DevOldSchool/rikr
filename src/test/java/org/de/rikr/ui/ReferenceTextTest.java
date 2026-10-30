package org.de.rikr.ui;

import org.de.rikr.reference.ReferenceKind;
import org.de.rikr.reference.ReferenceResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceTextTest {
    @Test
    void showsMembersWithJavaTypes() {
        ReferenceResult caller = new ReferenceResult(ReferenceKind.CALLER, "dc.a(Lwab;B)V -> calls ek.b", null,
            null, null);

        assertEquals("a(wab, byte) void", ReferenceWorkspacePanel.memberText(caller));
        assertEquals("calls it", ReferenceWorkspacePanel.kindText(ReferenceKind.CALLER));
    }

    @Test
    void keepsFieldsAndClassRelationsAsTheyAre() {
        ReferenceResult read = new ReferenceResult(ReferenceKind.FIELD_READ, "dc.count -> reads ek.x", null, null,
            null);
        ReferenceResult extendsResult = new ReferenceResult(ReferenceKind.EXTENDS, "dc extends ek", null, null, null);

        assertEquals("count", ReferenceWorkspacePanel.memberText(read));
        assertEquals("dc extends ek", ReferenceWorkspacePanel.memberText(extendsResult));
    }
}
