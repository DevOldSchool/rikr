package org.de.rikr.ui;

import org.de.rikr.reference.ReferenceKind;
import org.de.rikr.reference.ReferenceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void showsFieldTypesAsJavaTypes() {
        ReferenceResult fieldType = new ReferenceResult(ReferenceKind.FIELD_TYPE, "aab.b Lwab;", null, null, null);

        assertEquals("b wab", ReferenceWorkspacePanel.memberText(fieldType));
    }

    @Test
    void mergesASignatureAndCodeUseIntoOneRow() {
        ReferenceResult signature = new ReferenceResult(ReferenceKind.METHOD_TYPE, "aab.a(Lwab;)V", null, null,
            null);
        ReferenceResult code = new ReferenceResult(ReferenceKind.INSTRUCTION_TYPE, "aab.a(Lwab;)V", null, null, null);
        ReferenceResult other = new ReferenceResult(ReferenceKind.INSTRUCTION_TYPE, "aab.c()V", null, null, null);

        List<ReferenceWorkspacePanel.Row> rows = ReferenceWorkspacePanel.rows(List.of(signature, code, other));

        assertEquals(2, rows.size());
        assertEquals("in signature and code", rows.getFirst().kindsText());
        assertEquals("in code", rows.get(1).kindsText());
    }
}
