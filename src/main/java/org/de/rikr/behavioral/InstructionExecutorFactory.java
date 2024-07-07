package org.de.rikr.behavioral;

import org.de.rikr.behavioral.executors.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstructionExecutorFactory {
    private final Map<Integer, InstructionExecutor> executorCache = new HashMap<>();
    private final List<Integer> skipCacheOpcodes;

    public InstructionExecutorFactory() {
        skipCacheOpcodes = new ArrayList<>();
        skipCacheOpcodes.add(Opcodes.NEW);
        skipCacheOpcodes.add(Opcodes.NEWARRAY);
        skipCacheOpcodes.add(Opcodes.ANEWARRAY);
        skipCacheOpcodes.add(Opcodes.INVOKESPECIAL);
        skipCacheOpcodes.add(Opcodes.INVOKEVIRTUAL);
        skipCacheOpcodes.add(Opcodes.INVOKESTATIC);
        skipCacheOpcodes.add(Opcodes.INVOKEINTERFACE);
    }

    public InstructionExecutor createExecutor(List<ClassNode> classNodes, AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();

        if (!skipCacheOpcodes.contains(opcode) && executorCache.containsKey(opcode)) {
            return executorCache.get(opcode);
        }

        InstructionExecutor executor;
        switch (opcode) {
            case Opcodes.ICONST_M1:
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
            case Opcodes.BIPUSH:
            case Opcodes.SIPUSH:
            case Opcodes.ACONST_NULL:
            case Opcodes.LDC:
                executor = new ConstantPushExecutor();
                break;
            case Opcodes.ILOAD:
            case Opcodes.ALOAD:
            case Opcodes.DLOAD:
            case Opcodes.FLOAD:
            case Opcodes.AALOAD:
            case Opcodes.BALOAD:
            case Opcodes.CALOAD:
            case Opcodes.DALOAD:
            case Opcodes.FALOAD:
            case Opcodes.IALOAD:
            case Opcodes.LALOAD:
            case Opcodes.SALOAD:
                executor = new LoadExecutor();
                break;
            case Opcodes.ISTORE:
            case Opcodes.ASTORE:
            case Opcodes.FSTORE:
            case Opcodes.DSTORE:
            case Opcodes.LSTORE:
            case Opcodes.AASTORE:
            case Opcodes.BASTORE:
            case Opcodes.CASTORE:
            case Opcodes.DASTORE:
            case Opcodes.FASTORE:
            case Opcodes.IASTORE:
            case Opcodes.LASTORE:
            case Opcodes.SASTORE:
                executor = new StoreExecutor();
                break;
            case Opcodes.GETFIELD:
            case Opcodes.PUTFIELD:
            case Opcodes.GETSTATIC:
            case Opcodes.PUTSTATIC:
                executor = new FieldExecutor();
                break;
            case Opcodes.IADD:
            case Opcodes.ISUB:
            case Opcodes.IMUL:
            case Opcodes.IDIV:
            case Opcodes.IREM:
            case Opcodes.IAND:
            case Opcodes.IOR:
            case Opcodes.IXOR:
            case Opcodes.LADD:
            case Opcodes.LSUB:
            case Opcodes.LMUL:
            case Opcodes.LDIV:
            case Opcodes.LREM:
            case Opcodes.LAND:
            case Opcodes.LOR:
            case Opcodes.LXOR:
            case Opcodes.DADD:
            case Opcodes.DSUB:
            case Opcodes.DMUL:
            case Opcodes.DDIV:
            case Opcodes.DREM:
            case Opcodes.FADD:
            case Opcodes.FSUB:
            case Opcodes.FMUL:
            case Opcodes.FDIV:
            case Opcodes.FREM:
            case Opcodes.ISHL:
            case Opcodes.ISHR:
            case Opcodes.IUSHR:
                executor = new ArithmeticExecutor();
                break;
            case Opcodes.IF_ICMPEQ:
            case Opcodes.IF_ICMPNE:
            case Opcodes.IF_ICMPLT:
            case Opcodes.IF_ICMPGE:
            case Opcodes.IF_ICMPGT:
            case Opcodes.IF_ICMPLE:
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IF_ACMPEQ:
            case Opcodes.IF_ACMPNE:
                executor = new ComparisonExecutor();
                break;
            case Opcodes.GOTO:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                executor = new ControlFlowExecutor();
                break;
            case Opcodes.ARRAYLENGTH:
                executor = new ArrayExecutor();
                break;
            case Opcodes.NEW:
            case Opcodes.NEWARRAY:
            case Opcodes.ANEWARRAY:
                executor = new ObjectCreationExecutor(classNodes);
                break;
            case Opcodes.DUP:
            case Opcodes.DUP_X1:
            case Opcodes.DUP_X2:
            case Opcodes.DUP2:
            case Opcodes.DUP2_X1:
            case Opcodes.DUP2_X2:
            case Opcodes.POP:
            case Opcodes.POP2:
            case Opcodes.SWAP:
                executor = new StackManipulationExecutor();
                break;
            case Opcodes.INVOKESPECIAL:
            case Opcodes.INVOKEVIRTUAL:
            case Opcodes.INVOKESTATIC:
            case Opcodes.INVOKEINTERFACE:
                executor = new MethodExecutor(classNodes);
                break;
            case Opcodes.I2B:
            case Opcodes.I2C:
            case Opcodes.I2S:
                executor = new TypeConversionExecutor();
                break;
            case Opcodes.IINC:
                executor = new LocalVariableExecutor();
                break;
            case Opcodes.RETURN:
            case Opcodes.IRETURN:
            case Opcodes.ARETURN:
            case Opcodes.MONITORENTER:
            case Opcodes.MONITOREXIT:
                executor = new ReturnExecutor();
                break;
            default:
                executor = new DefaultExecutor();
                if (instruction.getOpcode() != -1) {
                    System.out.println("Unhandled instruction: " + opcode + " (" + Printer.OPCODES[opcode] + ")");
                }
                break;
        }

        executorCache.put(opcode, executor);
        return executor;
    }
}
