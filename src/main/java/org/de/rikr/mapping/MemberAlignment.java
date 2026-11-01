package org.de.rikr.mapping;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Two classes' fields and methods lined up for comparing a mapping candidate: each member beside a member of the same
 * shape in the other class, and the members with none on rows of their own.
 * <p>
 * A shape leaves out names and the JAR's own class names, since both change between obfuscated versions; whether a
 * member is static, JDK types and primitives are compared.
 */
public final class MemberAlignment {
    private MemberAlignment() {
    }

    public enum Section {
        FIELDS,
        METHODS
    }

    /**
     * One row of the comparison, as people read members: {@code static int count}, {@code void run(int, String)}.
     *
     * @param source the source class's member, or null when only the other class has one of this shape
     * @param target the other class's member, or null when it has none of this shape
     */
    public record Row(Section section, String source, String target) {
        public boolean matched() {
            return source != null && target != null;
        }
    }

    /**
     * Fields first, then methods. Within each, the source class's members keep their order, each followed by the
     * first unpaired member of the same shape in {@code target}; the target's members left over come last.
     *
     * @param target the class compared with, or null for the source class alone
     */
    public static List<Row> align(ClassNode source, ClassNode target) {
        List<Row> rows = new ArrayList<>();
        align(Section.FIELDS, fields(source), target == null ? List.of() : fields(target), rows);
        align(Section.METHODS, methods(source), target == null ? List.of() : methods(target), rows);
        return rows;
    }

    private record Member(String shape, String text) {
    }

    private static void align(Section section, List<Member> source, List<Member> target, List<Row> rows) {
        List<Member> unpaired = new ArrayList<>(target);
        for (Member member : source) {
            Member partner = unpaired.stream().filter(other -> other.shape().equals(member.shape())).findFirst()
                .orElse(null);
            unpaired.remove(partner);
            rows.add(new Row(section, member.text(), partner == null ? null : partner.text()));
        }
        unpaired.forEach(member -> rows.add(new Row(section, null, member.text())));
    }

    private static List<Member> fields(ClassNode classNode) {
        List<Member> members = new ArrayList<>();
        for (FieldNode field : classNode.fields) {
            String modifier = Modifier.isStatic(field.access) ? "static " : "";
            Type type = Type.getType(field.desc);
            members.add(new Member(modifier + shape(type),
                modifier + ClassNodeUtil.simpleName(type) + " " + field.name));
        }
        return members;
    }

    private static List<Member> methods(ClassNode classNode) {
        List<Member> members = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            String modifier = Modifier.isStatic(method.access) ? "static " : "";
            Type type = Type.getMethodType(method.desc);
            List<String> shapes = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (Type argument : type.getArgumentTypes()) {
                shapes.add(shape(argument));
                names.add(ClassNodeUtil.simpleName(argument));
            }
            members.add(new Member(modifier + "(" + String.join(",", shapes) + ")" + shape(type.getReturnType()),
                modifier + ClassNodeUtil.simpleName(type.getReturnType()) + " " + method.name
                    + "(" + String.join(", ", names) + ")"));
        }
        return members;
    }

    private static String shape(Type type) {
        Type element = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        String dimensions = type.getSort() == Type.ARRAY ? "[]".repeat(type.getDimensions()) : "";
        if (element.getSort() == Type.OBJECT && !element.getInternalName().startsWith("java/")) {
            return "object" + dimensions;
        }
        return element.getClassName() + dimensions;
    }
}
