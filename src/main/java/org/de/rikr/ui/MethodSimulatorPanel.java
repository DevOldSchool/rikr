package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.behavioral.MethodSimulator;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MethodSimulatorPanel extends JPanel {
    private final Rikr controller;
    private final JTextArea stackArea;
    private final JTextArea localVariableArea;

    private MethodNode methodNode;
    private MethodSimulator methodSimulator;
    private int lineNumber;
    private int previousOffset;

    public MethodSimulatorPanel(Rikr controller) {
        this.controller = controller;

        setLayout(new GridLayout(2, 1));

        stackArea = new JTextArea();
        stackArea.setEditable(false);
        stackArea.setBackground(Theme.BACKGROUND_COLOR);

        localVariableArea = new JTextArea();
        localVariableArea.setEditable(false);
        localVariableArea.setBackground(Theme.BACKGROUND_COLOR);

        JScrollPane stackScrollPane = new JScrollPane(stackArea);
        stackScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        JScrollPane localVariableScrollPane = new JScrollPane(localVariableArea);
        localVariableScrollPane.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        add(stackScrollPane);
        add(localVariableScrollPane);
    }

    public void init() {

    }

    public void setStackAreaText(Stack<Object> stack) {
        stackArea.setText("");

        for (Object object : stack) {
            if (object == null) {
                stackArea.append("null\n");
            } else {
                stackArea.append(object + "\n");
            }
        }
    }

    public void setLocalVariableAreaText(Map<Integer, Object> localVariables) {
        localVariableArea.setText("");

        for (Map.Entry<Integer, Object> entry : localVariables.entrySet()) {
            localVariableArea.append(entry.getKey() + ": " + entry.getValue() + "\n");
        }
    }

    public void initSimulate(ClassNodeMutableTreeNode parentNode, MethodNode methodNode) {
        this.methodNode = methodNode;
        List<ClassNode> classes = controller.getClasses(parentNode.getJarName());

        stackArea.setText("");
        localVariableArea.setText("");

        lineNumber = controller.getUserInterface().getContentPanel().getLineNumberOfPattern(methodNode.name + methodNode.desc);
        methodSimulator = new MethodSimulator(classes);
        controller.getUserInterface().toggleMethodSimulatorVisibility(false);
    }

    public void stepForwards() {
        int instructionOffset = methodSimulator.stepForwards(methodNode);

        if (instructionOffset < 0) {
            return;
        } else if (instructionOffset == 0) {
            instructionOffset = previousOffset + 1;
        } else if (!methodNode.tryCatchBlocks.isEmpty()) {
            instructionOffset += methodNode.tryCatchBlocks.size();
        }

        controller.getUserInterface().getContentPanel().setCaretPositionToLineNumber(lineNumber + instructionOffset);
        controller.getUserInterface().setStackAreaText(methodSimulator.getStack());
        controller.getUserInterface().setLocalVariableAreaText(methodSimulator.getLocalVariables());

        previousOffset = instructionOffset;
    }

    public void stepBackwards() {
        int instructionOffset = methodSimulator.stepBackwards(previousOffset - 1);

        if (instructionOffset < 0) {
            return;
        }

        controller.getUserInterface().getContentPanel().setCaretPositionToLineNumber(lineNumber + instructionOffset);
        controller.getUserInterface().setStackAreaText(methodSimulator.getStack());
        controller.getUserInterface().setLocalVariableAreaText(methodSimulator.getLocalVariables());

        previousOffset = instructionOffset;
    }
}
