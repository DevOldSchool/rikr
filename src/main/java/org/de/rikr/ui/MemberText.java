package org.de.rikr.ui;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Members as people read them in headings and the status bar: {@code no(nm, int)} rather than {@code no(Lnm;I)V}.
 */
public final class MemberText {
    private MemberText() {
    }

    /**
     * The method's name and parameter types, without the return type: {@code no(nm, int)}.
     */
    public static String signature(MethodNode method) {
        return signature(method.name, method.desc);
    }

    public static String signature(String name, String descriptor) {
        List<String> arguments = new ArrayList<>();
        for (Type argument : Type.getMethodType(descriptor).getArgumentTypes()) {
            String type = argument.getClassName();
            arguments.add(type.substring(type.lastIndexOf('.') + 1));
        }
        return name + "(" + String.join(", ", arguments) + ")";
    }

    /**
     * The method with its class: {@code ar.no(nm, int)}.
     */
    public static String describe(ClassNode owner, MethodNode method) {
        return owner.name.replace('/', '.') + "." + signature(method);
    }
}
