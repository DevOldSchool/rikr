package org.de.rikr.ui;

import org.de.rikr.Rikr;
import org.de.rikr.behavioral.MethodSimulator;
import org.de.rikr.behavioral.SimulationException;
import org.de.rikr.settings.SettingsManager;
import org.de.rikr.symbol.InstructionLines;
import org.de.rikr.theme.Theme;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Steps through one method an instruction at a time, showing the operand stack and local variables after each
 * step and marking the next instruction in the bytecode view.
 */
public class MethodSimulatorPanel extends JPanel {
    private static final String STACK_HINT = "Right-click a method in the project tree and choose Simulate Method, "
        + "then Step forwards to run it one instruction at a time. The values on the stack show here.";
    private static final String LOCALS_HINT = "Each local variable's slot and value show here as you step.";

    private final Rikr controller;
    private final JTextArea title = titleArea();
    private final JTextArea status = textArea("Simulator status");
    // Shown when the next branch depends on an unknown value, so the person stepping picks its direction.
    private final JPanel choice = new JPanel(new GridLayout(1, 2, 8, 0));
    private final JTextArea stackArea = textArea("Operand stack");
    private final JTextArea localVariableArea = textArea("Local variables");
    private final JLabel stackHeading = heading("Operand stack");
    private final JLabel localVariableHeading = heading("Local variables");

    private ClassNode owner;
    private MethodNode methodNode;
    private MethodSimulator methodSimulator;
    // The line of each instruction in the bytecode view, counted from the method's declaration.
    private int[] lines;
    // Why the last step failed; null when it didn't.
    private String problem;
    private Runnable onChange = () -> {
    };

    public MethodSimulatorPanel(Rikr controller) {
        this.controller = controller;
        setLayout(new BorderLayout());

        JScrollPane stackScrollPane = new JScrollPane(stackArea);
        stackScrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        JScrollPane localVariableScrollPane = new JScrollPane(localVariableArea);
        localVariableScrollPane.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.NORTH);
        top.add(status, BorderLayout.CENTER);
        JButton jump = new JButton("Jump");
        jump.addActionListener(e -> step(true));
        JButton next = new JButton("Don't jump");
        next.addActionListener(e -> step(false));
        // The panel is narrow, so keep the buttons' padding small enough for both labels to fit.
        jump.setMargin(new Insets(2, 6, 2, 6));
        next.setMargin(new Insets(2, 6, 2, 6));
        choice.setOpaque(false);
        choice.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        choice.add(jump);
        choice.add(next);
        choice.setVisible(false);
        top.add(choice, BorderLayout.SOUTH);

        JPanel sections = new JPanel(new GridLayout(2, 1));
        sections.setOpaque(false);
        sections.add(section(stackHeading, stackScrollPane));
        sections.add(section(localVariableHeading, localVariableScrollPane));

        add(top, BorderLayout.NORTH);
        add(sections, BorderLayout.CENTER);

        showHints();
        SettingsManager.addListener(s -> applyTheme());
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        return label;
    }

    // The method's name can be long, so the title wraps instead of being cut off.
    private static JTextArea titleArea() {
        JTextArea area = textArea("Simulated method");
        area.setFont(area.getFont().deriveFont(Font.BOLD));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        return area;
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

    /**
     * Run after every change to what can be stepped, so the step buttons can follow it.
     */
    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    // Until a method is simulated, each area says what it will show and how to start.
    private void showHints() {
        title.setText("Method simulator");
        status.setText("");
        status.setVisible(false);
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
        for (JTextArea area : new JTextArea[]{stackArea, localVariableArea, status, title}) {
            area.setBackground(Theme.BACKGROUND_COLOR);
            area.setForeground(text);
        }
        status.setForeground(Theme.CONTENT_HIGHLIGHT_TEXT_COLOR);
        title.setForeground(Theme.CONTENT_TEXT_COLOR);
        for (JLabel heading : new JLabel[]{stackHeading, localVariableHeading}) {
            heading.setForeground(Theme.CONTENT_TEXT_COLOR);
        }

        revalidate();
        repaint();
    }

    public void init() {

    }

    public void initSimulate(ClassNode owner, MethodNode methodNode) {
        this.owner = owner;
        this.methodNode = methodNode;
        methodSimulator = new MethodSimulator(owner.name, methodNode);
        lines = InstructionLines.of(methodNode);
        problem = null;
        controller.getUserInterface().toggleMethodSimulatorVisibility(false);
        showState();
    }

    /**
     * Stop simulating: clear the marked instruction and go back to the hints.
     */
    public void stop() {
        methodSimulator = null;
        methodNode = null;
        owner = null;
        problem = null;
        controller.getUserInterface().getContentPanel().clearMarkedLine();
        choice.setVisible(false);
        showHints();
        onChange.run();
    }

    // Stepping forwards is blocked while a branch waits for the person to choose its direction.
    public boolean canStepForwards() {
        return simulating() && problem == null && methodSimulator.position() >= 0
            && !methodSimulator.isUndecidedBranch();
    }

    public boolean canStepBackwards() {
        return simulating() && methodSimulator.steps() > 0;
    }

    public void stepForwards() {
        if (canStepForwards()) {
            step(null);
        }
    }

    // Runs the next instruction; jump says which way an undecided branch goes.
    private void step(Boolean jump) {
        AbstractInsnNode next = methodNode.instructions.get(methodSimulator.position());
        try {
            methodSimulator.stepForwards(jump);
        } catch (SimulationException e) {
            problem = "Can't simulate " + describe(next) + ": " + e.getMessage() + ".";
        }
        showState();
    }

    public void stepBackwards() {
        if (simulating() && methodSimulator.stepBackwards()) {
            problem = null;
            showState();
        }
    }

    private void showState() {
        title.setText(simpleName(owner.name) + "." + methodNode.name + methodNode.desc);
        status.setText(statusText());
        status.setVisible(true);
        choice.setVisible(problem == null && methodSimulator.isUndecidedBranch());
        stackArea.setText(stackText());
        localVariableArea.setText(localsText());
        stackArea.setCaretPosition(0);
        localVariableArea.setCaretPosition(0);
        applyTheme();

        var userInterface = controller.getUserInterface();
        if (userInterface.getContentPanel().getDisplayedClass() != owner) {
            userInterface.displayBytecode(owner);
        }
        // The view fills in later, so mark the line once it has.
        userInterface.getContentPanel().whenShown(this::markInstruction);
        onChange.run();
    }

    // Mark the next instruction, or the last one run once the method has finished.
    private void markInstruction() {
        if (!simulating()) {
            return;
        }
        ContentPanel content = controller.getUserInterface().getContentPanel();
        int position = methodSimulator.position();
        if (position < 0 || problem != null) {
            position = methodSimulator.lastPosition();
        }
        int declaration = content.getLineNumberOfPattern(methodNode.name + methodNode.desc);
        if (position < 0 || declaration < 0) {
            content.clearMarkedLine();
            return;
        }
        content.markLine(declaration + lines[position]);
    }

    private String statusText() {
        int steps = methodSimulator.steps();
        int position = methodSimulator.position();
        if (problem != null) {
            return problem + " Step backwards to undo the last step, or Close.";
        }
        String note = methodSimulator.note() == null ? "" : methodSimulator.note() + " ";
        if (methodSimulator.isFinished()) {
            return "Finished after " + plural(steps, "step") + ". " + note;
        }
        if (position < 0) {
            return steps == 0 ? "This method has no code to run."
                : "Ran off the end of the method after " + plural(steps, "step") + ".";
        }
        AbstractInsnNode next = methodNode.instructions.get(position);
        if (methodSimulator.isUndecidedBranch()) {
            return note + "Whether " + describe(next) + " jumps depends on an unknown value. Choose which way to go.";
        }
        String nextText = "Next: " + describe(next) + ".";
        return steps == 0
            ? "Step forwards runs the highlighted instruction. " + nextText
            : plural(steps, "step") + " run. " + note + nextText;
    }

    private String stackText() {
        List<Object> stack = methodSimulator.getStack();
        if (stack.isEmpty()) {
            return "Empty";
        }
        // Top first, as debuggers list a stack.
        StringBuilder text = new StringBuilder();
        for (int i = stack.size() - 1; i >= 0; i--) {
            text.append(format(stack.get(i)));
            if (i == stack.size() - 1) {
                text.append("   ← top");
            }
            text.append('\n');
        }
        return text.toString();
    }

    private String localsText() {
        Map<Integer, Object> values = methodSimulator.getLocalVariables();
        if (values.isEmpty()) {
            return "None";
        }
        StringBuilder text = new StringBuilder();
        values.forEach((slot, value) -> text.append(slot).append("   ").append(format(value)).append('\n'));
        return text.toString();
    }

    static String format(Object value) {
        return switch (value) {
            case null -> "null";
            case String string -> "\"" + string + "\"";
            case Long number -> number + "L";
            case Float number -> number + "f";
            case Double number -> number + "d";
            default -> value.toString();
        };
    }

    // An instruction as the bytecode view prints it, with owners shortened to their simple names.
    static String describe(AbstractInsnNode instruction) {
        String opcode = Printer.OPCODES[instruction.getOpcode()];
        return switch (instruction) {
            case FieldInsnNode field -> opcode + " " + simpleName(field.owner) + "." + field.name;
            case MethodInsnNode method -> opcode + " " + simpleName(method.owner) + "." + method.name;
            case TypeInsnNode type -> opcode + " " + simpleName(type.desc);
            case VarInsnNode variable -> opcode + " " + variable.var;
            case IntInsnNode number -> opcode + " " + number.operand;
            case IincInsnNode increment -> opcode + " " + increment.var + " " + increment.incr;
            case LdcInsnNode constant -> opcode + " " + format(constant.cst);
            default -> opcode;
        };
    }

    private static String simpleName(String name) {
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('.'));
        return name.substring(cut + 1);
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
