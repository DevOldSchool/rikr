package org.de.rikr.mapping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedNamesTest {
    @Test
    void recognisesNamesADeobfuscatorMadeUp() {
        for (String name : new String[]{"class12", "a/b/class3", "field34", "method5", "vmethod9758", "func_123_a",
            "field_70170_p"}) {
            assertTrue(GeneratedNames.isGenerated(name), name);
        }
    }

    @Test
    void takesTheShortShapeMostOfAJarsNamesShareAsObfuscated() {
        var obfuscated = GeneratedNames.obfuscatedIn(java.util.List.of("ao", "an", "bq", "zz", "x", "Node", "id"));
        var named = GeneratedNames.obfuscatedIn(java.util.List.of("Node", "x", "y", "id", "remove", "next"));

        assertTrue(obfuscated.test("ao"));
        assertTrue(obfuscated.test("id"));
        assertFalse(obfuscated.test("x"));
        assertFalse(obfuscated.test("Node"));
        assertFalse(named.test("id"));
        assertFalse(named.test("x"));
    }

    @Test
    void keepsNamesAPersonChose() {
        for (String name : new String[]{"Node", "x", "mouseOverColor2", "item2", "classic", "methodName", "a/Field1x"}) {
            assertFalse(GeneratedNames.isGenerated(name), name);
        }
    }
}
