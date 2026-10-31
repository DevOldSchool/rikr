package org.de.rikr.symbol;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.Arrays;
import java.util.List;

/**
 * Where each instruction of a method is printed in the bytecode view, counted in lines from the method's
 * declaration, so the simulator can point at the instruction it is about to run.
 * <p>
 * The method is printed on its own with the same {@link Textifier} the view uses, and the line count is taken
 * as each instruction is printed. Labels, line numbers and frames are instructions in ASM's list and each prints
 * one line, so every index has a line.
 */
public final class InstructionLines {
    private InstructionLines() {
    }

    /**
     * The line of each instruction, by its index in {@code method.instructions}, relative to the line that
     * declares the method. The declaration is the line containing the method's name and descriptor together.
     */
    public static int[] of(MethodNode method) {
        Textifier classPrinter = new Textifier();
        String[] exceptions = method.exceptions == null ? null : method.exceptions.toArray(new String[0]);
        Printer methodPrinter = classPrinter.visitMethod(method.access, method.name, method.desc, method.signature,
            exceptions);
        int[] lines = new int[method.instructions.size()];
        int[] next = {0};
        MethodVisitor counter = new MethodVisitor(Opcodes.ASM9, new TraceMethodVisitor(methodPrinter)) {
            // Called before each instruction is printed, in the order of the instruction list.
            private void mark() {
                if (next[0] < lines.length) {
                    lines[next[0]++] = lineCount(classPrinter.getText());
                }
            }

            @Override
            public void visitInsn(int opcode) {
                mark();
                super.visitInsn(opcode);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                mark();
                super.visitIntInsn(opcode, operand);
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                mark();
                super.visitVarInsn(opcode, varIndex);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                mark();
                super.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                mark();
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                        boolean isInterface) {
                mark();
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                               Object... bootstrapMethodArguments) {
                mark();
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                mark();
                super.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitLabel(Label label) {
                mark();
                super.visitLabel(label);
            }

            @Override
            public void visitLdcInsn(Object value) {
                mark();
                super.visitLdcInsn(value);
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                mark();
                super.visitIincInsn(varIndex, increment);
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                mark();
                super.visitTableSwitchInsn(min, max, dflt, labels);
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                mark();
                super.visitLookupSwitchInsn(dflt, keys, labels);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                mark();
                super.visitMultiANewArrayInsn(descriptor, numDimensions);
            }

            @Override
            public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                mark();
                super.visitFrame(type, numLocal, local, numStack, stack);
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                mark();
                super.visitLineNumber(line, start);
            }
        };
        method.accept(counter);

        StringBuilder text = new StringBuilder();
        flatten(classPrinter.getText(), text);
        int declaration = text.indexOf(method.name + method.desc);
        int declarationLine = declaration < 0 ? 0 : (int) text.substring(0, declaration).chars()
            .filter(c -> c == '\n').count();
        return Arrays.stream(lines).map(line -> line - declarationLine).toArray();
    }

    private static int lineCount(List<?> text) {
        int count = 0;
        for (Object part : text) {
            if (part instanceof List<?> nested) {
                count += lineCount(nested);
            } else {
                count += (int) part.toString().chars().filter(c -> c == '\n').count();
            }
        }
        return count;
    }

    private static void flatten(List<?> text, StringBuilder into) {
        for (Object part : text) {
            if (part instanceof List<?> nested) {
                flatten(nested, into);
            } else {
                into.append(part);
            }
        }
    }
}
