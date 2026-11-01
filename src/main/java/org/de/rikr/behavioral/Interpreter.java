package org.de.rikr.behavioral;

import org.de.rikr.behavioral.SimulatedValue.ArrayRef;
import org.de.rikr.behavioral.SimulatedValue.ClassConstant;
import org.de.rikr.behavioral.SimulatedValue.ObjectRef;
import org.de.rikr.behavioral.SimulatedValue.Unknown;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;
import java.util.function.UnaryOperator;

/**
 * Runs one instruction on a {@link Frame}, with the stack effect the JVM specification gives it. When an operand
 * is unknown the result is an {@link Unknown} of the right type, never a guess, and never a missing value, so the
 * stack below keeps its shape.
 * <p>
 * Calls are not followed: their arguments are popped and their result is unknown. A call may change any object
 * that existed before it, so what was known about those objects' fields and elements is forgotten.
 */
final class Interpreter {
    private static final Type OBJECT = Type.getObjectType("java/lang/Object");

    /**
     * What happened after an instruction ran.
     *
     * @param jump     the label to continue at, or null to continue with the next instruction
     * @param finished whether the method returned or threw
     * @param note     something worth telling the person stepping, or null
     */
    record Outcome(LabelNode jump, boolean finished, String note) {
        static final Outcome NEXT = new Outcome(null, false, null);

        static Outcome next(String note) {
            return new Outcome(null, false, note);
        }

        static Outcome jump(LabelNode label, String note) {
            return new Outcome(label, false, note);
        }

        static Outcome finish(String note) {
            return new Outcome(null, true, note);
        }
    }

    // The JVM throws this exception at this instruction; the simulator reports it and stops.
    private static final class Thrown extends RuntimeException {
        Thrown(String exception, String detail) {
            super("Throws " + exception + (detail == null ? "" : ": " + detail) + ".");
        }
    }

    private final Frame frame;

    Interpreter(Frame frame) {
        this.frame = frame;
    }

    /**
     * Whether a jump would be taken, judged from the stack without changing it. Null when it depends on a value
     * the simulator doesn't know.
     */
    static Boolean decide(JumpInsnNode jump, List<Object> stack) {
        int opcode = jump.getOpcode();
        Object top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        Object second = stack.size() < 2 ? null : stack.get(stack.size() - 2);
        return switch (opcode) {
            case Opcodes.GOTO -> true;
            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE ->
                top instanceof Integer value ? compare(opcode - Opcodes.IFEQ, value, 0) : null;
            case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                 Opcodes.IF_ICMPLE -> second instanceof Integer left && top instanceof Integer right
                ? compare(opcode - Opcodes.IF_ICMPEQ, left, right) : null;
            case Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE -> {
                Boolean same = sameReference(second, top);
                yield same == null ? null : same == (opcode == Opcodes.IF_ACMPEQ);
            }
            case Opcodes.IFNULL, Opcodes.IFNONNULL -> {
                Boolean isNull = isNull(top);
                yield isNull == null ? null : isNull == (opcode == Opcodes.IFNULL);
            }
            default -> throw new SimulationException(Printer.OPCODES[opcode] + " is not supported");
        };
    }

    // The six comparisons in the order the JVM numbers them: eq, ne, lt, ge, gt, le.
    private static boolean compare(int which, int left, int right) {
        return switch (which) {
            case 0 -> left == right;
            case 1 -> left != right;
            case 2 -> left < right;
            case 3 -> left >= right;
            case 4 -> left > right;
            default -> left <= right;
        };
    }

    private static Boolean isNull(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof Unknown ? null : false;
    }

    private static Boolean sameReference(Object left, Object right) {
        if (left instanceof Unknown || right instanceof Unknown) {
            return null;
        }
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof String || right instanceof String) {
            if (!(left instanceof String) || !(right instanceof String)) {
                return false;
            }
            // Equal string constants are the same interned object; equal strings built at run time may not be.
            return left.equals(right) ? Boolean.TRUE : null;
        }
        return left.equals(right);
    }

    /**
     * Run an instruction.
     *
     * @param jumpIfUnknown for a conditional jump that depends on an unknown value: whether to take it. Null
     *                      means continue with the next instruction.
     */
    Outcome execute(AbstractInsnNode instruction, Boolean jumpIfUnknown) {
        try {
            return run(instruction, jumpIfUnknown);
        } catch (Thrown thrown) {
            return Outcome.finish(thrown.getMessage());
        }
    }

    private Outcome run(AbstractInsnNode instruction, Boolean jumpIfUnknown) {
        int opcode = instruction.getOpcode();
        switch (opcode) {
            case Opcodes.NOP -> {
            }
            case Opcodes.ACONST_NULL -> push(null);
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2, Opcodes.ICONST_3,
                 Opcodes.ICONST_4, Opcodes.ICONST_5 -> push(opcode - Opcodes.ICONST_0);
            case Opcodes.LCONST_0, Opcodes.LCONST_1 -> push((long) (opcode - Opcodes.LCONST_0));
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> push((float) (opcode - Opcodes.FCONST_0));
            case Opcodes.DCONST_0, Opcodes.DCONST_1 -> push((double) (opcode - Opcodes.DCONST_0));
            case Opcodes.BIPUSH, Opcodes.SIPUSH -> push(((IntInsnNode) instruction).operand);
            case Opcodes.LDC -> push(constant(((LdcInsnNode) instruction).cst));

            case Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.ALOAD -> {
                int slot = ((VarInsnNode) instruction).var;
                Type type = loadType(opcode - Opcodes.ILOAD);
                push(frame.locals.containsKey(slot) ? frame.locals.get(slot) : new Unknown(type, "local " + slot));
            }
            case Opcodes.ISTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.ASTORE -> {
                int slot = ((VarInsnNode) instruction).var;
                Object value = pop();
                frame.locals.put(slot, value);
                if (SimulatedValue.size(value) == 2) {
                    frame.locals.remove(slot + 1);
                }
            }
            case Opcodes.IINC -> {
                IincInsnNode increment = (IincInsnNode) instruction;
                frame.locals.compute(increment.var, (slot, value) -> value instanceof Integer number
                    ? (Object) (number + increment.incr) : new Unknown(Type.INT_TYPE));
            }

            case Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD, Opcodes.BALOAD,
                 Opcodes.CALOAD, Opcodes.SALOAD -> arrayLoad(opcode);
            case Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE,
                 Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE -> arrayStore(opcode);

            case Opcodes.POP -> popWords(1);
            case Opcodes.POP2 -> popWords(2);
            case Opcodes.DUP -> duplicate(1, 0);
            case Opcodes.DUP_X1 -> duplicate(1, 1);
            case Opcodes.DUP_X2 -> duplicate(1, 2);
            case Opcodes.DUP2 -> duplicate(2, 0);
            case Opcodes.DUP2_X1 -> duplicate(2, 1);
            case Opcodes.DUP2_X2 -> duplicate(2, 2);
            case Opcodes.SWAP -> {
                Object top = pop();
                Object second = pop();
                push(top);
                push(second);
            }

            case Opcodes.IADD -> binary(Integer.class, Type.INT_TYPE, Integer::sum);
            case Opcodes.ISUB -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a - b);
            case Opcodes.IMUL -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a * b);
            case Opcodes.IDIV -> {
                requireNonZero(peek());
                binary(Integer.class, Type.INT_TYPE, (a, b) -> a / b);
            }
            case Opcodes.IREM -> {
                requireNonZero(peek());
                binary(Integer.class, Type.INT_TYPE, (a, b) -> a % b);
            }
            case Opcodes.IAND -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a & b);
            case Opcodes.IOR -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a | b);
            case Opcodes.IXOR -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a ^ b);
            case Opcodes.ISHL -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a << b);
            case Opcodes.ISHR -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a >> b);
            case Opcodes.IUSHR -> binary(Integer.class, Type.INT_TYPE, (a, b) -> a >>> b);
            case Opcodes.INEG -> unary(Integer.class, Type.INT_TYPE, a -> -a);

            case Opcodes.LADD -> binary(Long.class, Type.LONG_TYPE, Long::sum);
            case Opcodes.LSUB -> binary(Long.class, Type.LONG_TYPE, (a, b) -> a - b);
            case Opcodes.LMUL -> binary(Long.class, Type.LONG_TYPE, (a, b) -> a * b);
            case Opcodes.LDIV -> {
                requireNonZero(peek());
                binary(Long.class, Type.LONG_TYPE, (a, b) -> a / b);
            }
            case Opcodes.LREM -> {
                requireNonZero(peek());
                binary(Long.class, Type.LONG_TYPE, (a, b) -> a % b);
            }
            case Opcodes.LAND -> binary(Long.class, Type.LONG_TYPE, (a, b) -> a & b);
            case Opcodes.LOR -> binary(Long.class, Type.LONG_TYPE, (a, b) -> a | b);
            case Opcodes.LXOR -> binary(Long.class, Type.LONG_TYPE, (a, b) -> a ^ b);
            case Opcodes.LSHL -> longShift((a, b) -> a << b);
            case Opcodes.LSHR -> longShift((a, b) -> a >> b);
            case Opcodes.LUSHR -> longShift((a, b) -> a >>> b);
            case Opcodes.LNEG -> unary(Long.class, Type.LONG_TYPE, a -> -a);

            case Opcodes.FADD -> binary(Float.class, Type.FLOAT_TYPE, Float::sum);
            case Opcodes.FSUB -> binary(Float.class, Type.FLOAT_TYPE, (a, b) -> a - b);
            case Opcodes.FMUL -> binary(Float.class, Type.FLOAT_TYPE, (a, b) -> a * b);
            case Opcodes.FDIV -> binary(Float.class, Type.FLOAT_TYPE, (a, b) -> a / b);
            case Opcodes.FREM -> binary(Float.class, Type.FLOAT_TYPE, (a, b) -> a % b);
            case Opcodes.FNEG -> unary(Float.class, Type.FLOAT_TYPE, a -> -a);
            case Opcodes.DADD -> binary(Double.class, Type.DOUBLE_TYPE, Double::sum);
            case Opcodes.DSUB -> binary(Double.class, Type.DOUBLE_TYPE, (a, b) -> a - b);
            case Opcodes.DMUL -> binary(Double.class, Type.DOUBLE_TYPE, (a, b) -> a * b);
            case Opcodes.DDIV -> binary(Double.class, Type.DOUBLE_TYPE, (a, b) -> a / b);
            case Opcodes.DREM -> binary(Double.class, Type.DOUBLE_TYPE, (a, b) -> a % b);
            case Opcodes.DNEG -> unary(Double.class, Type.DOUBLE_TYPE, a -> -a);

            case Opcodes.I2L -> convert(Integer.class, Type.LONG_TYPE, a -> (long) a);
            case Opcodes.I2F -> convert(Integer.class, Type.FLOAT_TYPE, a -> (float) a);
            case Opcodes.I2D -> convert(Integer.class, Type.DOUBLE_TYPE, a -> (double) a);
            case Opcodes.L2I -> convert(Long.class, Type.INT_TYPE, a -> (int) (long) a);
            case Opcodes.L2F -> convert(Long.class, Type.FLOAT_TYPE, a -> (float) a);
            case Opcodes.L2D -> convert(Long.class, Type.DOUBLE_TYPE, a -> (double) a);
            case Opcodes.F2I -> convert(Float.class, Type.INT_TYPE, a -> (int) (float) a);
            case Opcodes.F2L -> convert(Float.class, Type.LONG_TYPE, a -> (long) (float) a);
            case Opcodes.F2D -> convert(Float.class, Type.DOUBLE_TYPE, a -> (double) a);
            case Opcodes.D2I -> convert(Double.class, Type.INT_TYPE, a -> (int) (double) a);
            case Opcodes.D2L -> convert(Double.class, Type.LONG_TYPE, a -> (long) (double) a);
            case Opcodes.D2F -> convert(Double.class, Type.FLOAT_TYPE, a -> (float) (double) a);
            case Opcodes.I2B -> convert(Integer.class, Type.INT_TYPE, a -> (int) (byte) (int) a);
            case Opcodes.I2C -> convert(Integer.class, Type.INT_TYPE, a -> (int) (char) (int) a);
            case Opcodes.I2S -> convert(Integer.class, Type.INT_TYPE, a -> (int) (short) (int) a);

            case Opcodes.LCMP -> compareValues(Long.class, Long::compare);
            case Opcodes.FCMPL, Opcodes.FCMPG -> compareValues(Float.class,
                (a, b) -> a.isNaN() || b.isNaN() ? (opcode == Opcodes.FCMPG ? 1 : -1) : Float.compare(a, b));
            case Opcodes.DCMPL, Opcodes.DCMPG -> compareValues(Double.class,
                (a, b) -> a.isNaN() || b.isNaN() ? (opcode == Opcodes.DCMPG ? 1 : -1) : Double.compare(a, b));

            case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                 Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT,
                 Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE, Opcodes.IFNULL, Opcodes.IFNONNULL,
                 Opcodes.GOTO -> {
                return jump((JumpInsnNode) instruction, jumpIfUnknown);
            }
            case Opcodes.TABLESWITCH -> {
                TableSwitchInsnNode table = (TableSwitchInsnNode) instruction;
                Object key = pop();
                if (!(key instanceof Integer value)) {
                    return Outcome.jump(table.dflt, "The switch key is unknown, so this took the default case.");
                }
                return Outcome.jump(value >= table.min && value <= table.max
                    ? table.labels.get(value - table.min) : table.dflt, null);
            }
            case Opcodes.LOOKUPSWITCH -> {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) instruction;
                Object key = pop();
                if (!(key instanceof Integer value)) {
                    return Outcome.jump(lookup.dflt, "The switch key is unknown, so this took the default case.");
                }
                int index = lookup.keys.indexOf(value);
                return Outcome.jump(index < 0 ? lookup.dflt : lookup.labels.get(index), null);
            }
            case Opcodes.JSR, Opcodes.RET ->
                throw new SimulationException(Printer.OPCODES[opcode] + " is obsolete and not supported");

            case Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.ARETURN -> {
                // The value stays on the stack, where the person stepping can see it and a comparison can check it.
                return Outcome.finish("Returns " + describe(peek()) + ".");
            }
            case Opcodes.RETURN -> {
                return Outcome.finish("Returns.");
            }
            case Opcodes.ATHROW -> {
                Object exception = pop();
                requireNonNull(exception, null);
                return Outcome.finish("Throws " + describe(exception)
                    + ". Exception handlers aren't followed.");
            }

            case Opcodes.GETSTATIC -> {
                FieldInsnNode field = (FieldInsnNode) instruction;
                String key = Frame.staticKey(field.owner, field.name, field.desc);
                push(frame.heap.containsKey(key) ? frame.heap.get(key)
                    : new Unknown(Type.getType(field.desc), simpleOwner(field.owner) + "." + field.name));
            }
            case Opcodes.PUTSTATIC -> {
                FieldInsnNode field = (FieldInsnNode) instruction;
                frame.heap.put(Frame.staticKey(field.owner, field.name, field.desc), pop());
            }
            case Opcodes.GETFIELD -> {
                FieldInsnNode field = (FieldInsnNode) instruction;
                Object object = pop();
                requireNonNull(object, "reading " + field.name);
                String key = object instanceof ObjectRef ref ? Frame.fieldKey(ref.id(), field.name, field.desc) : null;
                push(key != null && frame.heap.containsKey(key) ? frame.heap.get(key)
                    : new Unknown(Type.getType(field.desc), owner(object) + "." + field.name));
            }
            case Opcodes.PUTFIELD -> {
                FieldInsnNode field = (FieldInsnNode) instruction;
                Object value = pop();
                Object object = pop();
                requireNonNull(object, "writing " + field.name);
                if (object instanceof ObjectRef ref) {
                    frame.heap.put(Frame.fieldKey(ref.id(), field.name, field.desc), value);
                }
            }

            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC, Opcodes.INVOKEINTERFACE -> {
                MethodInsnNode call = (MethodInsnNode) instruction;
                return call(call.desc, opcode != Opcodes.INVOKESTATIC, simpleOwner(call.owner) + "." + call.name);
            }
            case Opcodes.INVOKEDYNAMIC -> {
                InvokeDynamicInsnNode call = (InvokeDynamicInsnNode) instruction;
                return call(call.desc, false, "invokedynamic " + call.name);
            }

            case Opcodes.NEW -> push(new ObjectRef(frame.nextObjectId++,
                Type.getObjectType(((TypeInsnNode) instruction).desc)));
            case Opcodes.NEWARRAY -> newArray(Type.getType("[" + primitive(((IntInsnNode) instruction).operand)));
            case Opcodes.ANEWARRAY -> {
                Type element = Type.getObjectType(((TypeInsnNode) instruction).desc);
                newArray(Type.getType("[" + element.getDescriptor()));
            }
            case Opcodes.MULTIANEWARRAY -> {
                MultiANewArrayInsnNode multi = (MultiANewArrayInsnNode) instruction;
                Object length = null;
                for (int i = 0; i < multi.dims; i++) {
                    length = pop();
                    requireNonNegative(length);
                }
                int id = frame.nextObjectId++;
                // The inner arrays aren't modelled, so no element is known.
                frame.heap.put(Frame.anyElementKey(id), true);
                push(new ArrayRef(id, Type.getType(multi.desc), length instanceof Integer known ? known : null));
            }
            case Opcodes.ARRAYLENGTH -> {
                Object array = pop();
                requireNonNull(array, "reading the length of an array");
                push(array instanceof ArrayRef ref && ref.length() != null
                    ? ref.length() : new Unknown(Type.INT_TYPE));
            }
            case Opcodes.CHECKCAST -> {
                Type type = Type.getObjectType(((TypeInsnNode) instruction).desc);
                if (peek() instanceof Unknown unknown) {
                    // The value is now known to be of the cast type, though not what it is.
                    pop();
                    push(new Unknown(type, unknown.source()));
                }
            }
            case Opcodes.INSTANCEOF -> {
                Type type = Type.getObjectType(((TypeInsnNode) instruction).desc);
                Object value = pop();
                if (value == null) {
                    push(0);
                } else if (value instanceof ObjectRef ref && ref.type().equals(type)
                    || value instanceof ArrayRef array && array.type().equals(type)
                    || value instanceof String && type.getInternalName().equals("java/lang/String")) {
                    push(1);
                } else {
                    // Without the class hierarchy, a subtype can't be told from an unrelated type.
                    push(new Unknown(Type.INT_TYPE));
                }
            }
            case Opcodes.MONITORENTER, Opcodes.MONITOREXIT -> requireNonNull(pop(), "locking");
            default -> throw new SimulationException(
                (opcode >= 0 ? Printer.OPCODES[opcode] : "Instruction " + opcode) + " is not supported");
        }
        return Outcome.NEXT;
    }

    private Outcome jump(JumpInsnNode jump, Boolean jumpIfUnknown) {
        Boolean taken = decide(jump, frame.stack);
        int opcode = jump.getOpcode();
        int operands = opcode == Opcodes.GOTO ? 0
            : opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ACMPNE ? 2 : 1;
        for (int i = 0; i < operands; i++) {
            pop();
        }
        if (taken == null) {
            boolean chosen = Boolean.TRUE.equals(jumpIfUnknown);
            String note = "The condition depends on an unknown value; "
                + (jumpIfUnknown == null ? "continued with the next instruction."
                : chosen ? "you chose to jump." : "you chose to continue.");
            return chosen ? Outcome.jump(jump.label, note) : Outcome.next(note);
        }
        return taken ? Outcome.jump(jump.label, null) : Outcome.NEXT;
    }

    private Outcome call(String descriptor, boolean hasReceiver, String name) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        for (int i = arguments.length - 1; i >= 0; i--) {
            pop();
        }
        if (hasReceiver) {
            requireNonNull(pop(), "calling " + name);
        }
        // The call may change any object that already exists.
        frame.callBarrier = frame.nextObjectId;
        frame.heap.clear();
        Type result = Type.getReturnType(descriptor);
        if (result.getSort() == Type.VOID) {
            return Outcome.next("Calls aren't followed, so anything " + name + " changes is now unknown.");
        }
        push(new Unknown(result, name + "()"));
        return Outcome.next("Calls aren't followed, so the result of " + name + " is unknown.");
    }

    private void arrayLoad(int opcode) {
        Object index = pop();
        Object array = pop();
        requireNonNull(array, "reading an array element");
        Type element = switch (opcode) {
            case Opcodes.LALOAD -> Type.LONG_TYPE;
            case Opcodes.FALOAD -> Type.FLOAT_TYPE;
            case Opcodes.DALOAD -> Type.DOUBLE_TYPE;
            case Opcodes.AALOAD -> elementType(array);
            default -> Type.INT_TYPE;
        };
        if (!(array instanceof ArrayRef ref) || !(index instanceof Integer position)) {
            push(new Unknown(element));
            return;
        }
        requireInBounds(ref, position);
        String key = Frame.elementKey(ref.id(), position);
        if (frame.heap.containsKey(key)) {
            push(frame.heap.get(key));
        } else if (ref.id() < frame.callBarrier || frame.heap.containsKey(Frame.anyElementKey(ref.id()))) {
            // A call since the array was made, or a write at an unknown index, may have changed this element.
            push(new Unknown(element));
        } else {
            push(defaultValue(ref.type().getElementType()));
        }
    }

    private void arrayStore(int opcode) {
        Object value = pop();
        Object index = pop();
        Object array = pop();
        requireNonNull(array, "writing an array element");
        if (!(array instanceof ArrayRef ref)) {
            return;
        }
        if (!(index instanceof Integer position)) {
            frame.heap.put(Frame.anyElementKey(ref.id()), true);
            return;
        }
        requireInBounds(ref, position);
        if (value instanceof Integer number) {
            // Narrow as the JVM does when it stores into a byte, boolean, char or short array.
            value = switch (opcode) {
                case Opcodes.BASTORE -> ref.type().getElementType().getSort() == Type.BOOLEAN
                    ? number & 1 : (int) (byte) (int) number;
                case Opcodes.CASTORE -> (int) (char) (int) number;
                case Opcodes.SASTORE -> (int) (short) (int) number;
                default -> number;
            };
        }
        frame.heap.put(Frame.elementKey(ref.id(), position), value);
    }

    private void newArray(Type type) {
        Object length = pop();
        requireNonNegative(length);
        push(new ArrayRef(frame.nextObjectId++, type, length instanceof Integer known ? known : null));
    }

    private static Type elementType(Object array) {
        Type type = array instanceof ArrayRef ref ? ref.type()
            : array instanceof Unknown unknown ? unknown.type() : null;
        if (type == null || type.getSort() != Type.ARRAY) {
            return OBJECT;
        }
        return Type.getType(type.getDescriptor().substring(1));
    }

    private static Object defaultValue(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> 0;
            case Type.LONG -> 0L;
            case Type.FLOAT -> 0f;
            case Type.DOUBLE -> 0d;
            default -> null;
        };
    }

    private static Object constant(Object value) {
        return switch (value) {
            case Integer ignored -> value;
            case Long ignored -> value;
            case Float ignored -> value;
            case Double ignored -> value;
            case String ignored -> value;
            case Type type when type.getSort() != Type.METHOD -> new ClassConstant(type);
            case ConstantDynamic dynamic -> new Unknown(Type.getType(dynamic.getDescriptor()), "constant " + dynamic.getName());
            default -> new Unknown(OBJECT, "constant");
        };
    }

    private static Type loadType(int offset) {
        return switch (offset) {
            case 0 -> Type.INT_TYPE;
            case 1 -> Type.LONG_TYPE;
            case 2 -> Type.FLOAT_TYPE;
            case 3 -> Type.DOUBLE_TYPE;
            default -> OBJECT;
        };
    }

    private static String primitive(int arrayType) {
        return switch (arrayType) {
            case Opcodes.T_BOOLEAN -> "Z";
            case Opcodes.T_CHAR -> "C";
            case Opcodes.T_FLOAT -> "F";
            case Opcodes.T_DOUBLE -> "D";
            case Opcodes.T_BYTE -> "B";
            case Opcodes.T_SHORT -> "S";
            case Opcodes.T_INT -> "I";
            case Opcodes.T_LONG -> "J";
            default -> throw new SimulationException("Unknown array type " + arrayType);
        };
    }

    private <T> void binary(Class<T> kind, Type type, BinaryOperator<T> operation) {
        Object right = pop();
        Object left = pop();
        push(kind.isInstance(left) && kind.isInstance(right)
            ? operation.apply(kind.cast(left), kind.cast(right)) : new Unknown(type));
    }

    private void longShift(BinaryOperator<Long> operation) {
        Object distance = pop();
        Object value = pop();
        push(value instanceof Long number && distance instanceof Integer bits
            ? operation.apply(number, (long) bits) : new Unknown(Type.LONG_TYPE));
    }

    private <T> void unary(Class<T> kind, Type type, UnaryOperator<T> operation) {
        Object value = pop();
        push(kind.isInstance(value) ? operation.apply(kind.cast(value)) : new Unknown(type));
    }

    private <T> void convert(Class<T> from, Type to, Function<T, Object> conversion) {
        Object value = pop();
        push(from.isInstance(value) ? conversion.apply(from.cast(value)) : new Unknown(to));
    }

    private <T> void compareValues(Class<T> kind, ToIntBiFunction<T, T> comparison) {
        Object right = pop();
        Object left = pop();
        push(kind.isInstance(left) && kind.isInstance(right)
            ? (Object) comparison.applyAsInt(kind.cast(left), kind.cast(right)) : new Unknown(Type.INT_TYPE));
    }

    // Pops values making up this many stack words: one long takes two, as do two ints. Bottom first.
    private List<Object> popWords(int words) {
        List<Object> values = new ArrayList<>();
        int taken = 0;
        while (taken < words) {
            Object value = pop();
            values.add(0, value);
            taken += SimulatedValue.size(value);
        }
        return values;
    }

    // The DUP family: copy the top words and insert the copy below the next words.
    private void duplicate(int words, int below) {
        List<Object> top = popWords(words);
        List<Object> under = below == 0 ? List.of() : popWords(below);
        top.forEach(this::push);
        under.forEach(this::push);
        top.forEach(this::push);
    }

    private static void requireNonZero(Object divisor) {
        if (Objects.equals(divisor, 0) || Objects.equals(divisor, 0L)) {
            throw new Thrown("ArithmeticException", "/ by zero");
        }
    }

    private static void requireNonNull(Object value, String doing) {
        if (value == null) {
            throw new Thrown("NullPointerException", doing == null ? null : doing + " on null");
        }
    }

    private static void requireNonNegative(Object length) {
        if (length instanceof Integer number && number < 0) {
            throw new Thrown("NegativeArraySizeException", String.valueOf(number));
        }
    }

    private static void requireInBounds(ArrayRef array, int index) {
        if (index < 0 || array.length() != null && index >= array.length()) {
            throw new Thrown("ArrayIndexOutOfBoundsException",
                "index " + index + " for length " + (array.length() == null ? "?" : array.length()));
        }
    }

    private static String owner(Object object) {
        return object instanceof ObjectRef ref && ref.id() == 0 ? "this" : describe(object);
    }

    private static String simpleOwner(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    private static String describe(Object value) {
        return value instanceof String string ? "\"" + string + "\"" : String.valueOf(value);
    }

    private Object peek() {
        if (frame.stack.isEmpty()) {
            throw new SimulationException("The stack is empty");
        }
        return frame.stack.get(frame.stack.size() - 1);
    }

    private Object pop() {
        if (frame.stack.isEmpty()) {
            throw new SimulationException("The stack is empty");
        }
        return frame.stack.remove(frame.stack.size() - 1);
    }

    private void push(Object value) {
        frame.stack.add(value);
    }
}
