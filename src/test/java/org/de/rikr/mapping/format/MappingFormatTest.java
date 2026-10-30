package org.de.rikr.mapping.format;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MappingFormatTest {
    private final NameMappings mappings = new NameMappings(List.of(
        new NameMappings.ClassMapping("a", "game/Player",
            List.of(new NameMappings.MemberMapping("b", "[La;", "party")),
            List.of(new NameMappings.MemberMapping("c", "(La;I)Lb;", "attack"))),
        new NameMappings.ClassMapping("b", "b",
            List.of(new NameMappings.MemberMapping("d", "I", "health")),
            List.of())
    ));

    @Test
    void writesTinyV2() {
        assertEquals("""
            tiny\t2\t0\tofficial\tnamed
            c\ta\tgame/Player
            \tf\t[La;\tb\tparty
            \tm\t(La;I)Lb;\tc\tattack
            c\tb\tb
            \tf\tI\td\thealth
            """, MappingFormat.TINY_V2.write(mappings));
    }

    @Test
    void writesEnigmaWithoutANamedNameForUnrenamedClasses() {
        assertEquals("""
            CLASS a game/Player
            \tFIELD b party [La;
            \tMETHOD c attack (La;I)Lb;
            CLASS b
            \tFIELD d health I
            """, MappingFormat.ENIGMA.write(mappings));
    }

    @Test
    void writesProguardFromNamedJavaTypesToOriginalNames() {
        assertEquals("""
            game.Player -> a:
                game.Player[] party -> b
                b attack(game.Player,int) -> c
            b -> b:
                int health -> d
            """, MappingFormat.PROGUARD.write(mappings));
    }
}
