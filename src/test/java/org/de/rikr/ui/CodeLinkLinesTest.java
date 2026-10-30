package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Linking inside methods with line numbers, using the Java line each piece of bytecode was decompiled into.
 */
class CodeLinkLinesTest {
    private static final String JAVA = """
        class client {
           final void a(int var1, wab var2) {
              kb.a = 0;
              if (var1 <= 13) {
                 n(55);
              }
              ++xb;
           }
        }
        """;
    // The Java lines, from 0, and the class file lines they came from.
    private static final Map<Integer, List<Integer>> ORIGINALS = Map.of(2, List.of(11), 3, List.of(16),
        4, List.of(17), 6, List.of(19));
    private static final String BYTECODE = """
        class client {
          // access flags 0x10
          final a(ILwab;)V
           L0
            LINENUMBER 11 L0
            ICONST_0
            PUTSTATIC kb.a : I
           L1
            LINENUMBER 16 L1
            ILOAD 1
            BIPUSH 13
            IF_ICMPGT L2
           L3
            LINENUMBER 17 L3
            BIPUSH 55
            INVOKESTATIC client.n (I)V
           L2
            LINENUMBER 19 L2
            GETSTATIC client.xb : I
            ICONST_1
            IADD
            PUTSTATIC client.xb : I
            RETURN

          // access flags 0x8
          static n(I)V
            RETURN
        }
        """;

    private final ClassNode owner = owner();

    @Test
    void showsTheJavaAnInstructionReadsAs() {
        assertEquals("var1", toJava("ILOAD 1"));
        assertEquals("13", toJava("BIPUSH 13"));
        assertEquals("if", toJava("IF_ICMPGT"));
        assertEquals("n", toJava("INVOKESTATIC client.n"));
        assertEquals("55", toJava("BIPUSH 55"));
        assertEquals("a", toJava("PUTSTATIC kb.a"));
    }

    @Test
    void showsTheWholeJavaLineForAnInstructionWithNoWordOfItsOwn() {
        assertEquals("++xb;", toJava("IADD"));
    }

    @Test
    void showsTheInstructionAClickedJavaWordComesFrom() {
        assertEquals("BIPUSH 55", toBytecode("55"));
        assertEquals("ILOAD 1", toBytecode("var1 <="));
        assertEquals("IF_ICMPGT L2", toBytecode("if ("));
        assertEquals("GETSTATIC client.xb : I", toBytecode("xb;"));
        assertEquals("INVOKESTATIC client.n (I)V", toBytecode("n(55)"));
    }

    @Test
    void showsTheLinesFirstInstructionForJavaWithNoInstructionOfItsOwn() {
        assertEquals("ICONST_0", toBytecode("= 0"));
    }

    @Test
    void matchesALoopsCompareToItsLoop() {
        String java = """
            class client {
               final void a(int var1, wab var2) {
                  for(int var3 = 0; var3 < var1; ++var3) {
                  }
               }
            }
            """;
        String bytecode = """
            class client {
              final a(ILwab;)V
               L0
                LINENUMBER 5 L0
                ICONST_0
                ISTORE 3
               L1
                ILOAD 3
                ILOAD 1
                IF_ICMPGE L2
                IINC 3 1
                GOTO L1
               L2
                RETURN
            }
            """;
        Map<Integer, List<Integer>> originals = Map.of(2, List.of(5));

        CodeLink.Range toJava = CodeLink.bytecodeToJava(owner, bytecode, bytecode.indexOf("IF_ICMPGE"), java,
            originals).orElseThrow();
        assertEquals("for", java.substring(toJava.start(), toJava.end()));
        CodeLink.Range toBytecode = CodeLink.javaToBytecode(owner, java, java.indexOf("for"), bytecode, originals)
            .orElseThrow();
        assertEquals("IF_ICMPGE L2", bytecode.substring(toBytecode.start(), toBytecode.end()));
    }

    @Test
    void matchesOperatorsBothWays() {
        String java = """
            class client {
               final void a(int var1, wab var2) {
                  this.f = var1 > 0 ? new float[this.width * this.height + 1] : null;
                  int var3 = (var1 - 2) / 4 % 7 << 2 >>> 1 ^ -69 & var1 | 3;
               }
            }
            """;
        String bytecode = """
            class client {
              final a(ILwab;)V
               L0
                LINENUMBER 6 L0
                ALOAD 0
                ILOAD 1
                IFLE L1
                ALOAD 0
                GETFIELD client.width : I
                ALOAD 0
                GETFIELD client.height : I
                IMUL
                ICONST_1
                IADD
                NEWARRAY T_FLOAT
                GOTO L2
               L1
                ACONST_NULL
               L2
                PUTFIELD client.f : [F
               L3
                LINENUMBER 7 L3
                ILOAD 1
                ICONST_2
                ISUB
                ICONST_4
                IDIV
                BIPUSH 7
                IREM
                ICONST_2
                ISHL
                ICONST_1
                IUSHR
                BIPUSH -69
                ILOAD 1
                IAND
                IXOR
                ICONST_3
                IOR
                ISTORE 3
                RETURN
            }
            """;
        Map<Integer, List<Integer>> originals = Map.of(2, List.of(6), 3, List.of(7));

        for (String[] pair : new String[][]{{"IMUL", "*"}, {"IADD", "+"}, {"ISUB", "-"}, {"IDIV", "/"},
            {"IREM", "%"}, {"ISHL", "<<"}, {"IUSHR", ">>>"}, {"IAND", "&"}, {"IXOR", "^"}, {"IOR", "|"},
            {"IFLE", "?"}, {"NEWARRAY", "new"}, {"ACONST_NULL", "null"}, {"BIPUSH -69", "-69"}}) {
            CodeLink.Range toJava = CodeLink.bytecodeToJava(owner, bytecode, bytecode.indexOf(pair[0]), java,
                originals).orElseThrow();
            assertEquals(pair[1], java.substring(toJava.start(), toJava.end()), pair[0]);
        }
        for (String[] pair : new String[][]{{"* this", "IMUL"}, {"+ 1", "IADD"}, {"- 2", "ISUB"},
            {"? new", "IFLE L1"}, {">>> 1", "IUSHR"}, {"| 3", "IOR"}}) {
            CodeLink.Range toBytecode = CodeLink.javaToBytecode(owner, java, java.indexOf(pair[0]), bytecode,
                originals).orElseThrow();
            assertEquals(pair[1], bytecode.substring(toBytecode.start(), toBytecode.end()), pair[0]);
        }
    }

    @Test
    void namesLocalsTheWayFernFlowerDoes() {
        assertEquals("this", CodeLink.token("ALOAD 0", false));
        assertEquals("var0", CodeLink.token("ALOAD 0", true));
        assertEquals("var3", CodeLink.token("IINC 3 1", false));
        assertEquals("return", CodeLink.token("ARETURN", false));
        assertEquals("new", CodeLink.token("INVOKESPECIAL java/lang/Object.<init> ()V", false));
        assertEquals("String", CodeLink.token("CHECKCAST java/lang/String", false));
    }

    private String toJava(String instruction) {
        CodeLink.Range range = CodeLink.bytecodeToJava(owner, BYTECODE, BYTECODE.indexOf(instruction), JAVA,
            ORIGINALS).orElseThrow();
        return JAVA.substring(range.start(), range.end());
    }

    private String toBytecode(String javaText) {
        CodeLink.Range range = CodeLink.javaToBytecode(owner, JAVA, JAVA.indexOf(javaText), BYTECODE, ORIGINALS)
            .orElseThrow();
        return BYTECODE.substring(range.start(), range.end());
    }

    private static ClassNode owner() {
        ClassNode classNode = new ClassNode();
        classNode.name = "client";
        classNode.methods.add(new MethodNode(Opcodes.ACC_FINAL, "a", "(ILwab;)V", null, null));
        classNode.methods.add(new MethodNode(Opcodes.ACC_STATIC, "n", "(I)V", null, null));
        return classNode;
    }
}
