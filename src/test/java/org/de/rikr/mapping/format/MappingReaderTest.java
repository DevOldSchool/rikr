package org.de.rikr.mapping.format;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MappingReaderTest {
    private final NameMappings mappings = new NameMappings(List.of(
        new NameMappings.ClassMapping("a", "game/Player",
            List.of(new NameMappings.MemberMapping("b", "[La;", "party")),
            List.of(new NameMappings.MemberMapping("c", "(La;I)Lb;", "attack"))),
        new NameMappings.ClassMapping("b", "b",
            List.of(new NameMappings.MemberMapping("d", "I", "health")),
            List.of())
    ));

    @Test
    void readsBackWhatEachFormatWrites() throws Exception {
        for (MappingFormat format : MappingFormat.values()) {
            String text = format.write(mappings);

            assertEquals(format, MappingReader.detect(text));
            assertEquals(mappings, MappingReader.read(text), format.name());
        }
    }

    @Test
    void readsTinyWithSeveralNamespacesAndEmptyNames() throws Exception {
        String text = """
            tiny\t2\t0\tofficial\tintermediary\tnamed
            c\ta\tclass_1\tgame/Player
            \tc\tA player.
            \tf\tI\tb\tfield_1\t
            \tm\t()V\tc\tmethod_1\ttick
            \t\tp\t1\t\t\tticks
            """;

        assertEquals(new NameMappings(List.of(new NameMappings.ClassMapping("a", "game/Player",
            List.of(new NameMappings.MemberMapping("b", "I", "b")),
            List.of(new NameMappings.MemberMapping("c", "()V", "tick"))))), MappingReader.read(text));
    }

    @Test
    void readsEnigmaInnerClassesNestedUnderTheirOuterClass() throws Exception {
        String text = """
            CLASS a game/Player
            \tFIELD b health I
            \tCLASS c Inventory
            \t\tMETHOD d add (I)V
            \t\t\tARG 1 slot
            \tMETHOD e tick ()V
            """;

        assertEquals(new NameMappings(List.of(
            new NameMappings.ClassMapping("a", "game/Player",
                List.of(new NameMappings.MemberMapping("b", "I", "health")),
                List.of(new NameMappings.MemberMapping("e", "()V", "tick"))),
            new NameMappings.ClassMapping("a$c", "game/Player$Inventory",
                List.of(),
                List.of(new NameMappings.MemberMapping("d", "(I)V", "add")))
        )), MappingReader.read(text));
    }

    @Test
    void readsProguardLineNumbersAndSkipsInlinedMethods() throws Exception {
        String text = """
            # compiler: R8
            game.Player -> a:
                game.Player[] party -> b
                1:4:void tick(int,java.lang.String):10:13 -> c
                5:5:void game.Other.helper():20:20 -> c
            """;

        assertEquals(new NameMappings(List.of(new NameMappings.ClassMapping("a", "game/Player",
            List.of(new NameMappings.MemberMapping("b", "[La;", "party")),
            List.of(new NameMappings.MemberMapping("c", "(ILjava/lang/String;)V", "tick"))))), MappingReader.read(text));
    }

    @Test
    void rejectsTextInNoKnownFormat() {
        assertThrows(IOException.class, () -> MappingReader.read("hello world\n"));
    }
}
