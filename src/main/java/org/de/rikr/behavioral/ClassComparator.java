package org.de.rikr.behavioral;

import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClassComparator {

    public List<ClassNode> getMatchingClassNodes(List<ClassNode> classes, ClassNode classNodeToMatch) {
        List<ClassNode> matching = new ArrayList<>();

        boolean isInterfaceToMatch = Modifier.isInterface(classNodeToMatch.access);
        boolean isAbstractToMatch = Modifier.isAbstract(classNodeToMatch.access);
        boolean hasSuperClassToMatch = !classNodeToMatch.superName.equals("java/lang/Object");
        int interfaceCountToMatch = classNodeToMatch.interfaces.size();
        List<String> fieldsToMatch = getNonStaticFieldDescriptions(classNodeToMatch);
        List<String> methodsToMatch = getNonStaticMethodDescriptions(classNodeToMatch);


        for (ClassNode classNode : classes) {
            if (matches(classNode, isInterfaceToMatch, isAbstractToMatch, hasSuperClassToMatch, interfaceCountToMatch, fieldsToMatch, methodsToMatch)) {
                matching.add(classNode);
            }
        }

        return matching;
    }

    public List<String> getNonStaticFieldDescriptions(ClassNode classNode) {
        return classNode.fields.stream()
                .filter(field -> !Modifier.isStatic(field.access))
                .map(field -> replaceCustomTypes(field.desc))
                .collect(Collectors.toList());
    }

    public List<String> getNonStaticMethodDescriptions(ClassNode classNode) {
        return classNode.methods.stream()
                .filter(method -> !Modifier.isStatic(method.access))
                .map(method -> replaceCustomTypes(method.desc))
                .collect(Collectors.toList());
    }

    private boolean matches(ClassNode classNode, boolean isInterfaceToMatch, boolean isAbstractToMatch, boolean hasSuperClassToMatch, int interfaceCountToMatch, List<String> fieldsToMatch, List<String> methodsToMatch) {
        boolean isInterface = Modifier.isInterface(classNode.access);
        boolean isAbstract = Modifier.isAbstract(classNode.access);
        boolean hasSuperClass = !classNode.superName.equals("java/lang/Object");
        int interfaceCount = classNode.interfaces.size();
        List<String> fields = getNonStaticFieldDescriptions(classNode);
        List<String> methods = getNonStaticMethodDescriptions(classNode);

        return isInterface == isInterfaceToMatch &&
                isAbstract == isAbstractToMatch &&
                hasSuperClass == hasSuperClassToMatch &&
                interfaceCount == interfaceCountToMatch &&
                fields.equals(fieldsToMatch) &&
                methods.equals(methodsToMatch);
    }

    private String replaceCustomTypes(String desc) {
        // Regular expression to match L.*?; except when followed by java/
        String regex = "L(?!java/).*?;";

        // Replace matched patterns with L?;
        return desc.replaceAll(regex, "L?;");
    }
}
