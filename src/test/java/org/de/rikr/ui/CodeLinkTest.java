package org.de.rikr.ui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeLinkTest {
    private static final String JAVA = """
        public final class client extends df {
           static int a;

           static {
              a = 1;
           }

           client() {
              super();
           }

           static URL s(Applet var0) {
              if (var0 != null) {
                 return var0.getCodeBase();
              }
              return null;
           }

           final void s(int var1, String var2) throws IOException {
              for (int i = 0; i < var1; i++) {
              }
           }
        }
        """;

    private final ClassNode owner = owner();

    @Test
    void findsTheMethodAroundAJavaLine() {
        assertEquals("s", methodAt("return var0.getCodeBase()").name);
        assertEquals("(Ljava/applet/Applet;)Ljava/net/URL;", methodAt("return var0.getCodeBase()").desc);
        assertEquals("(ILjava/lang/String;)V", methodAt("for (int i").desc);
        assertEquals("<init>", methodAt("super();").name);
        assertEquals("<clinit>", methodAt("a = 1;").name);
        assertEquals(Optional.empty(), CodeLink.methodAtJava(owner, JAVA, JAVA.indexOf("static int a")));
    }

    @Test
    void findsAMethodDeclarationInJava() {
        MethodNode twoArguments = owner.methods.get(3);
        int start = JAVA.indexOf("s(int var1");

        assertArrayEquals(new int[]{start, start + 1},
            CodeLink.javaDeclaration(owner, twoArguments, JAVA).orElseThrow());
    }

    @Test
    void findsTheMethodAroundABytecodeOffset() {
        String bytecode = "class client\n  <init>()V\n    ALOAD 0\n"
            + "  s(Ljava/applet/Applet;)Ljava/net/URL;\n    ARETURN\n";

        assertEquals("<init>",
            CodeLink.methodAtBytecode(owner, bytecode, bytecode.indexOf("ALOAD")).orElseThrow().name);
        assertEquals("s", CodeLink.methodAtBytecode(owner, bytecode, bytecode.indexOf("ARETURN")).orElseThrow().name);
    }

    private static final String BYTECODE = """
        // class version 50.0 (50)
        public final class client extends df {

          // access flags 0x8
          static I a

          // access flags 0x8
          static <clinit>()V
            ICONST_1
            PUTSTATIC client.a : I
            RETURN

          // access flags 0x8
          static s(Ljava/applet/Applet;)Ljava/net/URL;
            ALOAD 0
            IFNULL L1
            ALOAD 0
            INVOKEVIRTUAL java/applet/Applet.getCodeBase ()Ljava/net/URL;
            ARETURN
           L1
            ACONST_NULL
            ARETURN

          // access flags 0x10
          final s(ILjava/lang/String;)V
            RETURN
        }
        """;

    @Test
    void linksAFieldDeclarationBothWays() {
        CodeLink.Range inBytecode = CodeLink.javaToBytecode(owner, JAVA, JAVA.indexOf("int a;") + 4, BYTECODE)
            .orElseThrow();
        assertEquals(BYTECODE.indexOf("I a") + 2, inBytecode.start());

        CodeLink.Range inJava = CodeLink.bytecodeToJava(owner, BYTECODE, BYTECODE.indexOf("I a"), JAVA).orElseThrow();
        assertEquals(JAVA.indexOf("int a;") + 4, inJava.start());
    }

    @Test
    void linksAClickedNameInsideAMethod() {
        CodeLink.Range call = CodeLink.javaToBytecode(owner, JAVA, JAVA.indexOf("getCodeBase") + 3, BYTECODE)
            .orElseThrow();
        assertEquals(BYTECODE.indexOf("INVOKEVIRTUAL java/applet/Applet.getCodeBase"), call.start());

        CodeLink.Range back = CodeLink.bytecodeToJava(owner, BYTECODE, BYTECODE.indexOf("getCodeBase"), JAVA)
            .orElseThrow();
        assertEquals(JAVA.indexOf("getCodeBase"), back.start());
    }

    @Test
    void fallsBackToTheMethodForNamesOnlyJavaHas() {
        CodeLink.Range local = CodeLink.javaToBytecode(owner, JAVA, JAVA.indexOf("i++"), BYTECODE)
            .orElseThrow();
        int header = BYTECODE.indexOf("s(ILjava/lang/String;)V");
        assertEquals(header, local.start());
    }

    private MethodNode methodAt(String text) {
        return CodeLink.methodAtJava(owner, JAVA, JAVA.indexOf(text)).orElseThrow();
    }

    private static ClassNode owner() {
        ClassNode classNode = new ClassNode();
        classNode.name = "client";
        classNode.fields.add(new FieldNode(Opcodes.ACC_STATIC, "a", "I", null, null));
        classNode.methods.add(new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null));
        classNode.methods.add(new MethodNode(0, "<init>", "()V", null, null));
        classNode.methods.add(new MethodNode(Opcodes.ACC_STATIC, "s", "(Ljava/applet/Applet;)Ljava/net/URL;", null,
            null));
        classNode.methods.add(new MethodNode(Opcodes.ACC_FINAL, "s", "(ILjava/lang/String;)V", null, null));
        return classNode;
    }
}
