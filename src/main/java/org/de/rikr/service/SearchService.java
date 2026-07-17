package org.de.rikr.service;

import org.de.rikr.utilities.ClassNodeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchService {
    public List<SearchResult> search(Map<String, List<ClassNode>> jarClassesMap, SearchQuery query) {
        List<SearchResult> results = new ArrayList<>();
        if (jarClassesMap == null || query == null || !query.isUsable()) {
            return results;
        }

        for (List<ClassNode> classes : jarClassesMap.values()) {
            for (ClassNode classNode : classes) {
                addClassResults(classNode, query, results);
            }
        }

        return results;
    }

    private void addClassResults(ClassNode classNode, SearchQuery query, List<SearchResult> results) {
        List<SearchResult> children = new ArrayList<>();
        boolean classNameMatch = matches(classNode.name, query);

        for (String interfaceName : classNode.interfaces) {
            if (matches(interfaceName, query)) {
                children.add(SearchResult.interfaceResult(
                    classNode,
                    interfaceName,
                    classNode.name,
                    ClassNodeUtil.getPattern(classNode, null, null)
                ));
            }
        }

        addAnnotationMatchesForClass(classNode, query, children);
        addFieldMatches(classNode, query, children);
        addMethodMatches(classNode, query, children);

        if (!children.isEmpty() || classNameMatch) {
            results.add(SearchResult.classResult(
                classNode,
                classNode.name,
                ClassNodeUtil.getPattern(classNode, null, null),
                true
            ));
            results.addAll(children);
        }
    }

    private void addFieldMatches(ClassNode classNode, SearchQuery query, List<SearchResult> children) {
        for (FieldNode fieldNode : classNode.fields) {
            if (matches(fieldNode.name, query)) {
                children.add(SearchResult.fieldResult(
                    classNode,
                    fieldNode,
                    fieldNode.name,
                    fieldNode.name,
                    ClassNodeUtil.getFieldNodePattern(fieldNode)
                ));
            }

            if (matches(fieldNode.desc, query)) {
                children.add(SearchResult.fieldResult(
                    classNode,
                    fieldNode,
                    fieldNode.desc,
                    fieldNode.desc,
                    ClassNodeUtil.getFieldNodePattern(fieldNode)
                ));
            }

            addAnnotationMatchesForField(classNode, fieldNode, query, children);
        }
    }

    private void addMethodMatches(ClassNode classNode, SearchQuery query, List<SearchResult> children) {
        for (MethodNode methodNode : classNode.methods) {
            boolean methodNameMatch = matches(methodNode.name, query);
            boolean methodDescMatch = matches(methodNode.desc, query);
            List<SearchResult> instructionChildren = getInstructionMatches(classNode, methodNode, query);

            addAnnotationMatchesForMethod(classNode, methodNode, query, children);

            if (methodNameMatch || methodDescMatch || !instructionChildren.isEmpty()) {
                children.add(SearchResult.methodResult(
                    classNode,
                    methodNode,
                    methodNode.name,
                    methodNode.name,
                    ClassNodeUtil.getMethodNodePattern(methodNode)
                ));
                children.addAll(instructionChildren);
            }
        }
    }

    private List<SearchResult> getInstructionMatches(ClassNode classNode, MethodNode methodNode, SearchQuery query) {
        List<SearchResult> instructionChildren = new ArrayList<>();

        for (AbstractInsnNode abstractInsnNode : methodNode.instructions) {
            if (abstractInsnNode instanceof LdcInsnNode ldcInsnNode) {
                addLdcMatch(classNode, ldcInsnNode, query, instructionChildren);
            }

            if (abstractInsnNode instanceof TypeInsnNode typeInsnNode && matches(typeInsnNode.desc, query)) {
                instructionChildren.add(SearchResult.instructionResult(
                    classNode,
                    typeInsnNode.desc,
                    typeInsnNode.desc,
                    "NEW " + typeInsnNode.desc
                ));
            }

            if (abstractInsnNode instanceof FieldInsnNode fieldInsnNode && matches(fieldInsnNode.name, query)) {
                instructionChildren.add(SearchResult.instructionResult(
                    classNode,
                    fieldInsnNode.name,
                    fieldInsnNode.name,
                    "GETSTATIC " + fieldInsnNode.owner + "." + fieldInsnNode.name
                ));
            }

            if (abstractInsnNode instanceof MethodInsnNode methodInsnNode && matches(methodInsnNode.name, query)) {
                instructionChildren.add(SearchResult.instructionResult(
                    classNode,
                    methodInsnNode.name,
                    methodInsnNode.name,
                    "INVOKEINTERFACE " + methodInsnNode.owner + "." + methodInsnNode.name + " " + methodInsnNode.desc
                ));
            }
        }

        return instructionChildren;
    }

    private void addLdcMatch(
        ClassNode classNode,
        LdcInsnNode ldcInsnNode,
        SearchQuery query,
        List<SearchResult> instructionChildren
    ) {
        if (ldcInsnNode.cst instanceof String stringCst && matches(stringCst, query)) {
            instructionChildren.add(SearchResult.instructionResult(
                classNode,
                stringCst,
                "\"" + stringCst + "\"",
                "LDC \"" + stringCst + "\""
            ));
        } else if (ldcInsnNode.cst instanceof Integer intCst && matches(intCst.toString(), query)) {
            instructionChildren.add(SearchResult.instructionResult(
                classNode,
                intCst.toString(),
                intCst.toString(),
                "LDC " + intCst
            ));
        } else if (ldcInsnNode.cst instanceof Long longCst && matches(longCst.toString(), query)) {
            instructionChildren.add(SearchResult.instructionResult(
                classNode,
                longCst.toString(),
                longCst.toString(),
                "LDC " + longCst
            ));
        }
    }

    private void addAnnotationMatchesForClass(ClassNode classNode, SearchQuery query, List<SearchResult> acc) {
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotationNode : classNode.visibleAnnotations) {
                addAnnotationResultIfMatch(classNode, null, null, annotationNode, query, acc);
            }
        }
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotationNode : classNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(classNode, null, null, annotationNode, query, acc);
            }
        }
    }

    private void addAnnotationMatchesForField(
        ClassNode owner,
        FieldNode fieldNode,
        SearchQuery query,
        List<SearchResult> acc
    ) {
        if (fieldNode.visibleAnnotations != null) {
            for (AnnotationNode annotationNode : fieldNode.visibleAnnotations) {
                addAnnotationResultIfMatch(owner, fieldNode, null, annotationNode, query, acc);
            }
        }
        if (fieldNode.invisibleAnnotations != null) {
            for (AnnotationNode annotationNode : fieldNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(owner, fieldNode, null, annotationNode, query, acc);
            }
        }
    }

    private void addAnnotationMatchesForMethod(
        ClassNode owner,
        MethodNode methodNode,
        SearchQuery query,
        List<SearchResult> acc
    ) {
        if (methodNode.visibleAnnotations != null) {
            for (AnnotationNode annotationNode : methodNode.visibleAnnotations) {
                addAnnotationResultIfMatch(owner, null, methodNode, annotationNode, query, acc);
            }
        }
        if (methodNode.invisibleAnnotations != null) {
            for (AnnotationNode annotationNode : methodNode.invisibleAnnotations) {
                addAnnotationResultIfMatch(owner, null, methodNode, annotationNode, query, acc);
            }
        }
    }

    private void addAnnotationResultIfMatch(
        ClassNode classNode,
        FieldNode fieldNode,
        MethodNode methodNode,
        AnnotationNode annotationNode,
        SearchQuery query,
        List<SearchResult> acc
    ) {
        String desc = annotationNode.desc != null ? annotationNode.desc : "";
        String simple = getAnnotationSimpleName(desc);
        String withAt = "@" + simple;
        String fqcn = getAnnotationClassName(desc);
        String fullDescSignature = formatAnnotation(annotationNode, true);
        String fullFqcnSignature = fqcn != null ? formatAnnotationWithName(annotationNode, fqcn) : null;

        if (matches(desc, query)
            || matches(simple, query)
            || matches(withAt, query)
            || (fqcn != null && matches(fqcn, query))
            || (fullDescSignature != null && matches(fullDescSignature, query))
            || (fullFqcnSignature != null && matches(fullFqcnSignature, query))
            || annotationElementsMatch(annotationNode, query)) {
            acc.add(annotationSearchResult(classNode, fieldNode, methodNode, withAt));
        }
    }

    private SearchResult annotationSearchResult(
        ClassNode classNode,
        FieldNode fieldNode,
        MethodNode methodNode,
        String text
    ) {
        if (methodNode != null) {
            return SearchResult.methodResult(
                classNode,
                methodNode,
                text,
                methodNode.name,
                ClassNodeUtil.getMethodNodePattern(methodNode)
            );
        }
        if (fieldNode != null) {
            return SearchResult.fieldResult(
                classNode,
                fieldNode,
                text,
                fieldNode.name,
                ClassNodeUtil.getFieldNodePattern(fieldNode)
            );
        }

        return new SearchResult(
            text,
            SearchResultIcon.CLASS,
            classNode,
            null,
            null,
            classNode.name,
            ClassNodeUtil.getPattern(classNode, null, null),
            1,
            true
        );
    }

    private String getAnnotationSimpleName(String desc) {
        if (desc == null) {
            return "";
        }
        int lastSlash = desc.lastIndexOf('/') + 1;
        int end = desc.endsWith(";") ? desc.length() - 1 : desc.length();
        int start = desc.startsWith("L") ? 1 : 0;
        int simpleStart = Math.max(lastSlash, start);
        if (simpleStart >= end) {
            return desc;
        }
        return desc.substring(simpleStart, end);
    }

    private String getAnnotationClassName(String desc) {
        if (desc == null) {
            return null;
        }
        String value = desc;
        if (value.startsWith("L")) {
            value = value.substring(1);
        }
        if (value.endsWith(";")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.replace('/', '.');
    }

    private String formatAnnotation(AnnotationNode annotationNode, boolean useDescriptorName) {
        String name = useDescriptorName
            ? annotationNode.desc != null ? annotationNode.desc : ""
            : getAnnotationClassName(annotationNode.desc);
        return formatAnnotationWithName(annotationNode, name);
    }

    private String formatAnnotationWithName(AnnotationNode annotationNode, String displayName) {
        StringBuilder sb = new StringBuilder();
        sb.append('@');
        sb.append(displayName != null ? displayName : "");
        if (annotationNode.values != null && !annotationNode.values.isEmpty()) {
            sb.append('(');
            for (int i = 0; i < annotationNode.values.size(); i += 2) {
                String key = String.valueOf(annotationNode.values.get(i));
                Object val = annotationNode.values.get(i + 1);
                sb.append(key).append('=').append(formatAnnotationValue(val));
                if (i + 2 < annotationNode.values.size()) {
                    sb.append(',').append(' ');
                }
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private String formatAnnotationValue(Object val) {
        if (val == null) {
            return "null";
        }
        if (val instanceof String stringValue) {
            return "\"" + stringValue + "\"";
        }
        if (val instanceof Type type) {
            return type.getDescriptor();
        }
        if (val instanceof String[] enumArr && enumArr.length == 2) {
            return enumArr[0] + "." + enumArr[1];
        }
        if (val instanceof AnnotationNode nested) {
            return formatAnnotation(nested, true);
        }
        if (val instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatAnnotationValue(list.get(i)));
                if (i + 1 < list.size()) {
                    sb.append(',').append(' ');
                }
            }
            sb.append('}');
            return sb.toString();
        }
        return String.valueOf(val);
    }

    private boolean annotationElementsMatch(AnnotationNode annotationNode, SearchQuery query) {
        if (annotationNode.values == null || annotationNode.values.isEmpty()) {
            return false;
        }
        for (int i = 0; i < annotationNode.values.size(); i += 2) {
            String key = String.valueOf(annotationNode.values.get(i));
            Object val = annotationNode.values.get(i + 1);
            String valStr = formatAnnotationValue(val);
            if (matches(key, query) || matches(valStr, query)) {
                return true;
            }
            if (val instanceof String stringValue && matches(stringValue, query)) {
                return true;
            }
            if (matches(key + "=" + valStr, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String first, SearchQuery query) {
        if (first == null || query.text() == null) {
            return false;
        }

        String haystack = query.matchCase() ? first : first.toLowerCase();
        String needle = query.matchCase() ? query.text() : query.text().toLowerCase();

        if (query.matchWord()) {
            return haystack.equals(needle);
        }

        return haystack.contains(needle);
    }
}
