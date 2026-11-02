package org.de.rikr.behavioral;

import org.de.rikr.behavioral.SimulatedValue.ObjectRef;
import org.de.rikr.behavioral.SimulatedValue.Unknown;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Runs one method an instruction at a time, without running the program: {@code this} is a reference, each
 * parameter is an unknown value of its type, and whatever is computed from unknown values is unknown too.
 * Every step can be undone.
 */
public class MethodSimulator {
    private final MethodNode method;
    private final Deque<Snapshot> history = new ArrayDeque<>();
    private Frame frame = new Frame();
    private int instructionPointer;
    private boolean finished;
    // What the last step wants to tell the person stepping, or null.
    private String note;

    /**
     * @param owner  the internal name of the class that declares the method, or null when it isn't known
     * @param method the method to run
     */
    public MethodSimulator(String owner, MethodNode method) {
        this.method = method;
        int slot = 0;
        if ((method.access & Opcodes.ACC_STATIC) == 0) {
            Type type = owner == null ? Type.getObjectType("java/lang/Object") : Type.getObjectType(owner);
            frame.locals.put(slot++, new ObjectRef(0, type));
        }
        Type[] parameters = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < parameters.length; i++) {
            frame.locals.put(slot, new Unknown(parameters[i], "parameter " + (i + 1)));
            slot += parameters[i].getSize();
        }
    }

    /**
     * The index of the next instruction stepping forwards runs, or -1 once the method has returned or thrown.
     * Labels, line numbers and frames are passed over, since they do nothing.
     */
    public int position() {
        if (finished) {
            return -1;
        }
        while (instructionPointer < method.instructions.size()
            && method.instructions.get(instructionPointer).getOpcode() < 0) {
            instructionPointer++;
        }
        return instructionPointer < method.instructions.size() ? instructionPointer : -1;
    }

    /**
     * Whether the next instruction is a conditional jump that depends on a value the simulator doesn't know,
     * so the person stepping has to say which way it goes.
     */
    public boolean isUndecidedBranch() {
        int position = position();
        return position >= 0 && method.instructions.get(position) instanceof JumpInsnNode jump
            && Interpreter.decide(jump, frame.stack) == null;
    }

    /**
     * Run the next instruction, continuing past a branch that depends on an unknown value.
     */
    public boolean stepForwards() {
        return stepForwards(null);
    }

    /**
     * Run the next instruction. Returns false when there is none left. If the instruction can't be simulated,
     * the state is left as it was before it and the {@link SimulationException} is passed on.
     *
     * @param jumpIfUnknown for a branch that depends on an unknown value, whether to take it; null continues
     */
    public boolean stepForwards(Boolean jumpIfUnknown) {
        int position = position();
        if (position < 0) {
            return false;
        }
        history.push(new Snapshot(position, frame.copy(), finished, note));
        try {
            run(position, jumpIfUnknown);
        } catch (RuntimeException e) {
            restore(history.pop());
            throw e;
        }
        return true;
    }

    /**
     * Run the next instruction without keeping a way back, for comparing methods over many steps.
     */
    boolean advance() {
        int position = position();
        if (position < 0) {
            return false;
        }
        run(position, null);
        return true;
    }

    private void run(int position, Boolean jumpIfUnknown) {
        AbstractInsnNode instruction = method.instructions.get(position);
        Interpreter.Outcome outcome = new Interpreter(frame).execute(instruction, jumpIfUnknown);
        note = outcome.note();
        if (outcome.finished()) {
            finished = true;
            instructionPointer = position;
        } else if (outcome.jump() != null) {
            instructionPointer = method.instructions.indexOf(outcome.jump());
        } else {
            instructionPointer = position + 1;
        }
    }

    /**
     * Undo the last step. Returns false when there is nothing to undo.
     */
    public boolean stepBackwards() {
        if (history.isEmpty()) {
            return false;
        }
        restore(history.pop());
        return true;
    }

    private void restore(Snapshot snapshot) {
        instructionPointer = snapshot.instructionPointer();
        frame = snapshot.frame();
        finished = snapshot.finished();
        note = snapshot.note();
    }

    /**
     * How many steps have been run and not undone.
     */
    public int steps() {
        return history.size();
    }

    /**
     * The index of the instruction the last step ran, or -1 before the first step.
     */
    public int lastPosition() {
        return history.isEmpty() ? -1 : history.peek().instructionPointer();
    }

    /**
     * Whether the method has returned or thrown.
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * What the last step wants to tell the person stepping, such as that a call's result is unknown; or null.
     */
    public String note() {
        return note;
    }

    /**
     * The operand stack, top last. Values are described in {@link SimulatedValue}.
     */
    public List<Object> getStack() {
        return Collections.unmodifiableList(frame.stack);
    }

    /**
     * The local variables by slot. A long or double fills its slot and the one after it.
     */
    public Map<Integer, Object> getLocalVariables() {
        return Collections.unmodifiableMap(frame.locals);
    }

    // The state before a step, so stepping backwards can put it back.
    private record Snapshot(int instructionPointer, Frame frame, boolean finished, String note) {
    }
}
