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
    void keepsNamesAPersonChose() {
        for (String name : new String[]{"Node", "x", "mouseOverColor2", "item2", "classic", "methodName", "a/Field1x"}) {
            assertFalse(GeneratedNames.isGenerated(name), name);
        }
    }
}
