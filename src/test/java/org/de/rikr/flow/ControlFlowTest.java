package org.de.rikr.flow;

import org.de.rikr.symbol.BytecodeText;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlFlowTest {

    @Test
    void anIfElseSplitsIntoThreeBlocksWithTrueAndFalseEdges() {
        MethodNode method = method("choose", "(I)I");
        Label otherwise = new Label();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitJumpInsn(Opcodes.IFEQ, otherwise);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(otherwise);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IRETURN);

        ControlFlowGraph graph = ControlFlowBuilder.build("a", method);

        assertEquals(3, graph.blocks().size());
        assertEquals(List.of("ILOAD 0", "IFEQ L0"), graph.blocks().get(0).lines());
        assertEquals(List.of(
            new ControlFlowGraph.Edge(0, 2, ControlFlowGraph.EdgeKind.TRUE, null),
            new ControlFlowGraph.Edge(0, 1, ControlFlowGraph.EdgeKind.FALSE, null)
        ), graph.edges());
        assertEquals("L0", graph.blocks().get(2).label());
        assertTrue(graph.blocks().get(1).exits(graph.edges()));
    }

    @Test
    void aLoopIsDrawnWithItsBackEdgeAndEveryOtherEdgePointingDown() {
        MethodNode method = loop();
        ControlFlowGraph graph = ControlFlowBuilder.build("a", method);

        GraphLayout.Result layout = GraphLayout.layout(graph, Collections.nCopies(graph.blocks().size(),
            new Dimension(120, 40)));

        assertEquals(1, layout.loops().stream().filter(loop -> loop).count());
        for (int index = 0; index < graph.edges().size(); index++) {
            Rectangle from = layout.boxes().get(graph.edges().get(index).from());
            Rectangle to = layout.boxes().get(graph.edges().get(index).to());
            if (layout.loops().get(index)) {
                assertTrue(to.y < from.y, "the loop edge goes up");
            } else {
                assertTrue(to.y >= from.y + from.height, "forward edges go down");
            }
        }
        for (Rectangle box : layout.boxes()) {
            for (Rectangle other : layout.boxes()) {
                assertTrue(box == other || !box.intersects(other), "blocks do not overlap");
            }
        }
    }

    @Test
    void wideLayersWrapOntoRows() {
        List<GraphLayout.Link> fanOut = new java.util.ArrayList<>();
        for (int callee = 1; callee <= 10; callee++) {
            fanOut.add(new GraphLayout.Link(0, callee));
        }

        GraphLayout.Result layout = GraphLayout.layout(11, fanOut, Collections.nCopies(11, new Dimension(80, 30)), 4);

        long rows = layout.boxes().stream().mapToInt(box -> box.y).distinct().count();
        assertEquals(4, rows, "the caller, then 10 callees in rows of 4, 4 and 2");
    }

    @Test
    void switchesHaveAnEdgePerCaseAndExceptionHandlersAnEdgePerCoveredBlock() {
        MethodNode method = method("pick", "(I)V");
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        Label zero = new Label();
        Label one = new Label();
        Label other = new Label();
        method.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");
        method.visitLabel(start);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitTableSwitchInsn(0, 1, other, zero, one);
        method.visitLabel(zero);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(one);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end);
        method.visitLabel(other);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);

        ControlFlowGraph graph = ControlFlowBuilder.build("a", method);

        List<String> switchLabels = graph.edges().stream()
            .filter(edge -> edge.kind() == ControlFlowGraph.EdgeKind.SWITCH)
            .map(ControlFlowGraph.Edge::label)
            .toList();
        long caught = graph.edges().stream()
            .filter(edge -> edge.kind() == ControlFlowGraph.EdgeKind.EXCEPTION)
            .filter(edge -> "java/lang/RuntimeException".equals(edge.label()))
            .count();
        assertEquals(List.of("0", "1", "default"), switchLabels);
        assertEquals(3, caught, "the switch and both cases are covered");
        assertTrue(graph.blocks().stream().allMatch(ControlFlowGraph.Block::reachable));

        ControlFlowGraph withoutHandlers = graph.withoutExceptionEdges();
        assertEquals(graph.blocks().size() - 1, withoutHandlers.blocks().size(), "the handler block is hidden");
        assertTrue(withoutHandlers.edges().stream()
            .noneMatch(edge -> edge.kind() == ControlFlowGraph.EdgeKind.EXCEPTION));
        assertEquals(3, withoutHandlers.edges().size());
    }

    @Test
    void deadCodeAfterAJumpIsMarkedUnreachable() {
        MethodNode method = method("dead", "()V");
        Label done = new Label();
        method.visitJumpInsn(Opcodes.GOTO, done);
        method.visitInsn(Opcodes.NOP);
        method.visitLabel(done);
        method.visitInsn(Opcodes.RETURN);

        ControlFlowGraph graph = ControlFlowBuilder.build("a", method);

        List<Boolean> reachable = graph.blocks().stream().map(ControlFlowGraph.Block::reachable).toList();
        assertEquals(List.of(true, false, true), reachable);
    }

    @Test
    void blockLabelsAreTheNamesTheBytecodeViewPrints() {
        ClassNode owner = new ClassNode();
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "a";
        owner.superName = "java/lang/Object";
        MethodNode method = loop();
        owner.methods.add(method);
        String text = BytecodeText.of(owner).text();

        ControlFlowGraph graph = ControlFlowBuilder.build("a", method);

        for (ControlFlowGraph.Block block : graph.blocks()) {
            if (block.label() != null) {
                assertTrue(text.contains("\n   " + block.label() + "\n"), block.label() + " in\n" + text);
            }
            for (String line : block.lines()) {
                assertTrue(text.contains(line), line);
            }
        }
        assertFalse(graph.blocks().isEmpty());
    }

    /**
     * {@code for (int i = 0; i < 10; i++) {} return}, with the loop test at the bottom as javac writes it.
     */
    private static MethodNode loop() {
        MethodNode method = method("count", "()V");
        Label test = new Label();
        Label body = new Label();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ISTORE, 0);
        method.visitJumpInsn(Opcodes.GOTO, test);
        method.visitLabel(body);
        method.visitIincInsn(0, 1);
        method.visitLabel(test);
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitIntInsn(Opcodes.BIPUSH, 10);
        method.visitJumpInsn(Opcodes.IF_ICMPLT, body);
        method.visitInsn(Opcodes.RETURN);
        return method;
    }

    private static MethodNode method(String name, String descriptor) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
    }
}
