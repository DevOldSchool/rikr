package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberTextTest {
    @Test
    void readsLikeJavaWithoutTheReturnType() {
        ClassNode owner = new ClassNode();
        owner.name = "ar";
        MethodNode method = new MethodNode(0, "no", "(Lnm;I[Ljava/lang/String;)V", null, null);
        assertEquals("no(nm, int, String[])", MemberText.signature(method));
        assertEquals("ar.no(nm, int, String[])", MemberText.describe(owner, method));
    }
}
