package org.de.rikr.symbol;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstructionLinesTest {
    @Test
    void pointsEachInstructionAtItsLineInTheBytecodeView() {
        ClassNode owner = new ClassNode();
        owner.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "a/Owner", null, "java/lang/Object", null);

        // A method before the one we look at, so the declaration isn't the first one printed.
        MethodNode before = new MethodNode(Opcodes.ACC_PUBLIC, "before", "()V", null, null);
        before.instructions.add(new InsnNode(Opcodes.RETURN));
        owner.methods.add(before);

        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "target", "(I)I", null, new String[]{"java/io/IOException"});
        method.visibleAnnotations = new ArrayList<>(List.of(new AnnotationNode("La/Marker;")));
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new LineNumberNode(12, start));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "a/Owner", "count", "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        // One of each kind of instruction, since each prints through its own visitor method.
        method.instructions.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/RuntimeException"}));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        method.instructions.add(new LdcInsnNode("text"));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false));
        method.instructions.add(new IincInsnNode(1, 2));
        method.instructions.add(new MultiANewArrayInsnNode("[[I", 2));
        method.instructions.add(new InvokeDynamicInsnNode("run", "()Ljava/lang/Runnable;",
            new Handle(Opcodes.H_INVOKESTATIC, "a/Owner", "boot", "()V", false)));
        LabelNode last = new LabelNode();
        method.instructions.add(new TableSwitchInsnNode(0, 1, last, last, last));
        method.instructions.add(new LookupSwitchInsnNode(last, new int[]{3}, new LabelNode[]{last}));
        method.instructions.add(last);
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/RuntimeException"));
        method.maxStack = 2;
        method.maxLocals = 2;
        owner.methods.add(method);

        String[] printed = BytecodeText.of(owner).text().split("\n", -1);
        int declaration = -1;
        for (int i = 0; i < printed.length; i++) {
            if (printed[i].contains("target(I)I")) {
                declaration = i;
                break;
            }
        }
        assertTrue(declaration >= 0);

        int[] lines = InstructionLines.of(method);
        assertEquals(method.instructions.size(), lines.length);
        for (int i = 0; i < lines.length; i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            String line = printed[declaration + lines[i]].trim();
            String expected = switch (instruction) {
                case LabelNode ignored -> "L";
                case LineNumberNode ignored -> "LINENUMBER";
                case FrameNode ignored -> "FRAME";
                default -> Printer.OPCODES[instruction.getOpcode()];
            };
            assertTrue(line.startsWith(expected), "instruction " + i + " printed as \"" + line + "\"");
        }
    }

    @Test
    void anAbstractMethodHasNoLines() {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "()V", null, null);
        assertEquals(0, InstructionLines.of(method).length);
    }
}
