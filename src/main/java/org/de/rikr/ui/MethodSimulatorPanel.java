package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.behavioral.MethodSimulator;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.theme.Theme;
import org.de.rikr.ui.model.ClassNodeMutableTreeNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MethodSimulatorPanel extends JPanel {
    private static final String STACK_HINT = "Right-click a method in the project tree and choose Simulate Method, "
        + "then Step forwards to run it one instruction at a time. The values on the stack show here.";
    private static final String LOCALS_HINT = "Each local variable's slot and value show here as you step.";

    private final Rikr controller;
    private final JTextArea stackArea;
    private final JTextArea localVariableArea;
    private final JLabel stackHeading = heading("Operand stack");
    private final JLabel localVariableHeading = heading("Local variables");

    private MethodNode methodNode;
    private MethodSimulator methodSimulator;
    private int lineNumber;
    private int previousOffset;

    public MethodSimulatorPanel(Rikr controller) {
        this.controller = controller;

        setLayout(new GridLayout(2, 1));

        stackArea = textArea("Operand stack");
        localVariableArea = textArea("Local variables");

        JScrollPane stackScrollPane = new JScrollPane(stackArea);
        stackScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        JScrollPane localVariableScrollPane = new JScrollPane(localVariableArea);
        localVariableScrollPane.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        add(section(stackHeading, stackScrollPane));
        add(section(localVariableHeading, localVariableScrollPane));

        showHints();
        applyTheme();
        SettingsManager.addListener(s -> applyTheme());
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        return label;
    }

    private static JTextArea textArea(String name) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        area.getAccessibleContext().setAccessibleName(name);
        return area;
    }

    private static JPanel section(JLabel heading, JScrollPane content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // Until a method is simulated, each area says what it will show and how to start.
    private void showHints() {
        stackArea.setText(STACK_HINT);
        localVariableArea.setText(LOCALS_HINT);
        applyTheme();
    }

    private boolean simulating() {
        return methodSimulator != null;
    }

    private void applyTheme() {
        setBackground(Theme.BACKGROUND_COLOR);
        Color text = simulating() ? Theme.CONTENT_TEXT_COLOR : Theme.CONTENT_HIGHLIGHT_TEXT_COLOR;
        for (JTextArea area : new JTextArea[]{stackArea, localVariableArea}) {
            area.setBackground(Theme.BACKGROUND_COLOR);
            area.setForeground(text);
        }
        stackHeading.setForeground(Theme.CONTENT_TEXT_COLOR);
        localVariableHeading.setForeground(Theme.CONTENT_TEXT_COLOR);

        revalidate();
        repaint();
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
        List<ClassNode> classes = controller.jars().classes(parentNode.getJarName());

        stackArea.setText("");
        localVariableArea.setText("");

        lineNumber = controller.getUserInterface().getContentPanel().getLineNumberOfPattern(methodNode.name + methodNode.desc);
        methodSimulator = new MethodSimulator(classes);
        applyTheme();
        controller.getUserInterface().toggleMethodSimulatorVisibility(false);
    }

    public void stepForwards() {
        if (!simulating()) {
            return;
        }
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
        if (!simulating()) {
            return;
        }
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
