package org.de.rikr.symbol;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeTextTest {
    private static final String SELF = "La;";
    private static final String METHOD = "(La;)La;";

    @Test
    void printsTheSameTextAsTextifier() {
        ClassNode classNode = classA();
        StringWriter plain = new StringWriter();
        classNode.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(plain)));

        assertEquals(plain.toString(), BytecodeText.of(classNode).text());
    }

    @Test
    void everySpanCoversTheNameOfItsSymbol() {
        for (ClassNode classNode : List.of(classA(), classB())) {
            BytecodeText bytecode = BytecodeText.of(classNode);
            assertTrue(bytecode.spans().size() > 5);
            for (SymbolSpan span : bytecode.spans()) {
                String named = bytecode.text().substring(span.start(), span.end());
                String expected = switch (span.symbol()) {
                    case Symbol.ClassSymbol symbol -> symbol.className();
                    case Symbol.FieldSymbol symbol -> symbol.name();
                    case Symbol.MethodSymbol symbol -> symbol.name();
                };
                assertEquals(expected, named, "span at " + span.start());
            }
        }
    }

    @Test
    void findsTheSymbolUnderEachPartOfAnInstruction() {
        BytecodeText bytecode = BytecodeText.of(classA());
        int line = bytecode.text().indexOf("GETFIELD a.a : La;");
        int owner = line + "GETFIELD ".length();
        int name = owner + "a.".length();
        int type = name + "a : L".length();

        assertEquals(Optional.of(new Symbol.ClassSymbol("a")), bytecode.symbolAt(owner));
        assertEquals(Optional.of(new Symbol.FieldSymbol("a", "a", SELF)), bytecode.symbolAt(name));
        assertEquals(Optional.of(new Symbol.ClassSymbol("a")), bytecode.symbolAt(type));
        assertEquals(Optional.empty(), bytecode.symbolAt(line));
    }

    @Test
    void marksDeclaredNamesNotLettersInsideKeywords() {
        BytecodeText bytecode = BytecodeText.of(classA());
        int method = bytecode.text().indexOf("public a(La;)La;") + "public ".length();
        int field = bytecode.text().indexOf("private La; a") + "private La; ".length();
        int declaration = bytecode.text().indexOf("public class a") + "public class ".length();

        assertEquals(Optional.of(new Symbol.MethodSymbol("a", "a", METHOD)), bytecode.symbolAt(method));
        assertEquals(Optional.of(new Symbol.FieldSymbol("a", "a", SELF)), bytecode.symbolAt(field));
        assertEquals(Optional.of(new Symbol.ClassSymbol("a")), bytecode.symbolAt(declaration));
        assertEquals(Optional.empty(), bytecode.symbolAt(bytecode.text().indexOf("public a(") + 3));
    }

    @Test
    void resolvesInheritedMembersToTheClassThatDeclaresThem() {
        ClassNode a = classA();
        ClassNode b = classB();
        List<ClassNode> classes = List.of(a, b);

        Declaration field = SymbolResolver.resolve(classes, new Symbol.FieldSymbol("b", "a", SELF)).orElseThrow();
        Declaration method = SymbolResolver.resolve(classes, new Symbol.MethodSymbol("b", "a", METHOD)).orElseThrow();

        assertSame(a.fields.getFirst(), ((Declaration.FieldDeclaration) field).field());
        assertSame(a, method.owner());
        assertSame(b, SymbolResolver.resolve(classes, new Symbol.ClassSymbol("b")).orElseThrow().owner());
        assertEquals(Optional.empty(), SymbolResolver.resolve(classes,
            new Symbol.MethodSymbol("b", "toString", "()Ljava/lang/String;")));
    }

    @Test
    void findsDeclarationsInAClassByName() {
        ClassNode a = classA();

        List<Declaration> named = SymbolResolver.declaredByName(a, "a");

        assertEquals(3, named.size());
        assertEquals(List.of(), SymbolResolver.declaredByName(a, "missing"));
    }

    private static ClassNode classA() {
        ClassNode classNode = classNode("a", "java/lang/Object");
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "a", SELF, null, null));
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "a", METHOD, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "a", "a", SELF));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "a", "a", METHOD, false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        classNode.methods.add(method);
        return classNode;
    }

    private static ClassNode classB() {
        ClassNode classNode = classNode("b", "a");
        classNode.interfaces.add("java/lang/Runnable");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, new String[]{"java/lang/Exception"});
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "b"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "b", "a", SELF));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "b", "a", METHOD, false));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "[La;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        classNode.methods.add(method);
        return classNode;
    }

    private static ClassNode classNode(String name, String superName) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V21;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        classNode.name = name;
        classNode.superName = superName;
        return classNode;
    }
}
