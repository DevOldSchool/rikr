package org.de.rikr.ui;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds what was clicked in the bytecode or the decompiled Java of a class in the other view: a field declaration, or
 * a place in a method.
 * <p>
 * Inside a method, the class file's line numbers narrow the search to the Java line made from the clicked bytecode,
 * or to the bytecode made from the clicked Java line. Within that, an instruction is matched to the Java it reads
 * as: a load or store to its variable ({@code var1}, or {@code this}), a call or field access to its name, a return to
 * {@code return}, a constant to its value; the occurrence with the same count is chosen. Methods without line numbers
 * are searched whole. When nothing matches, the line, or else the method's declaration, is shown.
 * <p>
 * Java methods are matched by name and parameter count; when overloads share both, the first is used.
 */
final class CodeLink {
    /**
     * A span of text to select, from {@code start} up to {@code end}.
     */
    record Range(int start, int end) {
    }

    // A method or constructor declaration on one line: modifiers and type, a name, then parameters.
    private static final Pattern DECLARATION = Pattern.compile(
        "(?m)^[ \\t]*(?:[\\w$<>\\[\\],.? ]+[ \\t])?([\\w$]+)\\(([^()]*)\\)[ \\t]*(?:throws [\\w$., ]+)?[ \\t]*\\{");
    private static final Pattern STATIC_BLOCK = Pattern.compile("(?m)^[ \\t]*static[ \\t]*\\{");
    // A field declaration on one line: modifiers and type, a name, perhaps a value.
    private static final Pattern FIELD = Pattern.compile(
        "(?m)^[ \\t]*(?:[\\w$<>\\[\\],.?]+[ \\t]+)+([\\w$]+)[ \\t]*(?:=[^;]*)?;[ \\t]*$");
    private static final Set<String> STATEMENTS = Set.of("if", "for", "while", "switch", "catch", "synchronized",
        "return", "new", "else", "do", "try");

    private CodeLink() {
    }

    /**
     * Where what is at {@code offset} in the Java is in the bytecode.
     */
    static Optional<Range> javaToBytecode(ClassNode owner, String java, int offset, String bytecode) {
        return javaToBytecode(owner, java, offset, bytecode, Map.of());
    }

    /**
     * @param originals for each Java line, counted from 0, the class file's line numbers it came from
     */
    static Optional<Range> javaToBytecode(ClassNode owner, String java, int offset, String bytecode,
                                          Map<Integer, List<Integer>> originals) {
        List<JavaMethod> methods = javaMethods(owner, java);
        Optional<JavaMethod> enclosing = enclosing(methods, offset);
        if (enclosing.isEmpty()) {
            return javaFieldAt(owner, java, offset).flatMap(field -> bytecodeField(field, bytecode));
        }
        JavaMethod method = enclosing.get();
        Optional<Range> header = bytecodeHeader(owner, method.method(), bytecode);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        int bodyStart = header.get().start();
        int bodyEnd = bytecodeMethodEnd(owner, bytecode, bodyStart);
        boolean isStatic = (method.method().access & Opcodes.ACC_STATIC) != 0;

        List<Integer> lines = originals.getOrDefault(lineIndex(java, offset), List.of());
        List<Range> regions = lineRegions(bytecode, bodyStart, bodyEnd, lines);
        boolean byLine = !regions.isEmpty();
        if (!byLine) {
            regions = List.of(new Range(bodyStart, bodyEnd));
        }
        Range word = tokenAt(java, offset);
        if (word != null) {
            String name = instructionToken(java.substring(word.start(), word.end()));
            int javaStart = byLine ? lineStart(java, offset) : method.start();
            int count = occurrences(java, name, javaStart, word.end()).size();
            List<Range> matching = new ArrayList<>();
            for (Range region : regions) {
                for (Range instruction : instructions(bytecode, region)) {
                    if (name.equals(token(bytecode.substring(instruction.start(), instruction.end()), isStatic))) {
                        matching.add(instruction);
                    }
                }
            }
            if (!matching.isEmpty()) {
                return Optional.of(matching.get(Math.max(0, Math.min(count, matching.size()) - 1)));
            }
        }
        if (byLine) {
            for (Range region : regions) {
                List<Range> found = instructions(bytecode, region);
                if (!found.isEmpty()) {
                    return Optional.of(found.getFirst());
                }
            }
        }
        return sameOccurrence(java, method.start(), offset, bytecode, bodyStart, bodyEnd).or(() -> header);
    }

    /**
     * Where what is at {@code offset} in the bytecode is in the Java.
     */
    static Optional<Range> bytecodeToJava(ClassNode owner, String bytecode, int offset, String java) {
        return bytecodeToJava(owner, bytecode, offset, java, Map.of());
    }

    /**
     * @param originals for each Java line, counted from 0, the class file's line numbers it came from
     */
    static Optional<Range> bytecodeToJava(ClassNode owner, String bytecode, int offset, String java,
                                          Map<Integer, List<Integer>> originals) {
        Optional<FieldNode> field = bytecodeFieldAt(owner, bytecode, offset);
        if (field.isPresent()) {
            return javaField(owner, field.get(), java);
        }
        Optional<MethodNode> method = methodAtBytecode(owner, bytecode, offset);
        if (method.isEmpty()) {
            return Optional.empty();
        }
        List<JavaMethod> methods = javaMethods(owner, java);
        Optional<JavaMethod> inJava = methods.stream().filter(candidate -> candidate.method() == method.get())
            .findFirst();
        if (inJava.isEmpty()) {
            return Optional.empty();
        }
        int start = bytecodeHeader(owner, method.get(), bytecode).map(Range::start).orElse(0);
        int javaEnd = methods.stream().mapToInt(JavaMethod::start).filter(at -> at > inJava.get().start())
            .min().orElse(java.length());
        if (lineStart(bytecode, offset) == lineStart(bytecode, start)) {
            return Optional.of(inJava.get().name());
        }
        int bodyEnd = bytecodeMethodEnd(owner, bytecode, start);
        boolean isStatic = (method.get().access & Opcodes.ACC_STATIC) != 0;

        // The Java lines made from the clicked instruction's source line, inside this method when possible, else
        // outside every method, as a field's initializer is for a constructor. Never inside another method: methods
        // may share line numbers, as obfuscators' often do, and FernFlower marks only one of their lines.
        int line = sourceLineAt(bytecode, start, offset);
        List<Range> javaLines = new ArrayList<>();
        if (line >= 0) {
            List<Range> outside = new ArrayList<>();
            originals.forEach((javaLine, numbers) -> {
                if (!numbers.contains(line)) {
                    return;
                }
                Range range = lineRange(java, javaLine);
                Optional<JavaMethod> in = enclosing(methods, range.start());
                if (in.isPresent() && in.get().method() == method.get()) {
                    javaLines.add(range);
                } else if (in.isEmpty()) {
                    outside.add(range);
                }
            });
            if (javaLines.isEmpty()) {
                javaLines.addAll(outside);
            }
            javaLines.sort((left, right) -> Integer.compare(left.start(), right.start()));
        }
        boolean byLine = !javaLines.isEmpty();
        // After the declaration's name, which would otherwise match a call or field of the same name.
        Range javaBody = new Range(inJava.get().name().end(), javaEnd);
        List<Range> javaRegions = byLine ? javaLines : List.of(javaBody);
        Range bytecodeRegion = byLine ? lineRegionAt(bytecode, start, bodyEnd, offset) : new Range(start, bodyEnd);

        Range clicked = instructionAt(bytecode, offset);
        String name = clicked == null ? null : token(bytecode.substring(clicked.start(), clicked.end()), isStatic);
        if (name != null) {
            Optional<Range> found = sameToken(bytecode, bytecodeRegion, clicked, name, isStatic, java, javaRegions);
            // FernFlower may mark only the first of the Java lines a source line became, as it does for an array
            // filled element by element; then the name is looked for in the whole method.
            if (found.isEmpty() && byLine) {
                found = sameToken(bytecode, new Range(start, bodyEnd), clicked, name, isStatic, java, List.of(javaBody));
            }
            if (found.isPresent()) {
                return found;
            }
        }
        if (byLine) {
            return Optional.of(trimmed(java, javaLines.getFirst()));
        }
        return sameOccurrence(bytecode, start, offset, java, inJava.get().start(), javaEnd)
            .or(() -> Optional.of(inJava.get().name()));
    }

    // The occurrence of an instruction's token in the Java regions with the same count as the instruction has
    // among those in the bytecode region, or the last occurrence if the Java has fewer.
    private static Optional<Range> sameToken(String bytecode, Range bytecodeRegion, Range clicked, String name,
                                             boolean isStatic, String java, List<Range> javaRegions) {
        int count = 0;
        for (Range instruction : instructions(bytecode, bytecodeRegion)) {
            if (instruction.start() > clicked.start()) {
                break;
            }
            if (name.equals(token(bytecode.substring(instruction.start(), instruction.end()), isStatic))) {
                count++;
            }
        }
        List<Range> found = new ArrayList<>();
        for (Range region : javaRegions) {
            found.addAll(occurrences(java, name, region.start(), region.end()));
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(Math.max(0, Math.min(count, found.size()) - 1)));
    }

    // Instruction lines start with an opcode in capitals; labels, line numbers and frames are not instructions.
    private static final Pattern INSTRUCTION = Pattern.compile("(?m)^[ \\t]*([A-Z][A-Z0-9_]*)(?:[ \\t].*)?$");
    private static final Set<String> NOT_INSTRUCTIONS = Set.of("LINENUMBER", "FRAME", "LOCALVARIABLE", "MAXSTACK",
        "MAXLOCALS", "TRYCATCHBLOCK");
    private static final Pattern LINE_NUMBER = Pattern.compile("(?m)^[ \\t]*LINENUMBER (\\d+) ");
    // Arithmetic and bitwise instructions, without their type letter, and the operator Java writes for them.
    private static final Map<String, String> OPERATORS = Map.ofEntries(Map.entry("ADD", "+"), Map.entry("SUB", "-"),
        Map.entry("MUL", "*"), Map.entry("DIV", "/"), Map.entry("REM", "%"), Map.entry("NEG", "-"),
        Map.entry("SHL", "<<"), Map.entry("SHR", ">>"), Map.entry("USHR", ">>>"), Map.entry("AND", "&"),
        Map.entry("OR", "|"), Map.entry("XOR", "^"));
    private static final Map<Character, String> CAST_TYPES = Map.of('I', "int", 'L', "long", 'F', "float",
        'D', "double", 'B', "byte", 'C', "char", 'S', "short");
    // How each operator is found in Java without matching a longer one: + but not ++, & but not &&, - but not a
    // negative literal, which FernFlower writes with no space before its digits. A compound assignment such as +=
    // counts as its operator.
    private static final Map<String, String> OPERATOR_PATTERNS = Map.ofEntries(
        Map.entry("+", "(?<!\\+)\\+(?!\\+)"), Map.entry("-", "(?<!-)-(?![-\\d>])"),
        Map.entry("*", "(?<!/)\\*(?!/)"), Map.entry("/", "(?<![/*])/(?![/*])"), Map.entry("%", "%"),
        Map.entry("<<", "(?<!<)<<(?!<)"), Map.entry(">>", "(?<!>)>>(?!>)"), Map.entry(">>>", ">>>"),
        Map.entry("&", "(?<!&)&(?!&)"), Map.entry("|", "(?<!\\|)\\|(?!\\|)"), Map.entry("^", "\\^"),
        // An index after a name or a closing bracket, not a type's [] nor the size in new T[n].
        Map.entry("[", "(?<!new [\\w$.]{1,200})(?<=[\\w$)\\]])\\[(?!\\])"));
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?[LFD]?");
    // Java writes long, float and double literals with a suffix, such as 671064862094971049L; bytecode doesn't.
    private static final Pattern SUFFIXED_NUMBER = Pattern.compile("(-?\\d+(?:\\.\\d+)?)[LFDlfd]");
    private static final Pattern UNSUFFIXED_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /**
     * What an instruction reads as in Java, or null when it has no word of its own there. Local variables are named
     * the way FernFlower names them, {@code var} and their slot.
     */
    static String token(String instruction, boolean isStatic) {
        String[] parts = instruction.strip().split("\\s+");
        String opcode = parts[0];
        String operand = parts.length > 1 ? parts[1] : "";
        if (opcode.matches("[AILFD](LOAD|STORE)") || opcode.equals("IINC")) {
            return operand.equals("0") && !isStatic ? "this" : "var" + operand;
        }
        if (opcode.startsWith("INVOKE") || opcode.matches("(GET|PUT)(FIELD|STATIC)")) {
            String member = operand.substring(operand.lastIndexOf('.') + 1);
            return member.equals("<init>") ? "new" : member.equals("<clinit>") ? null : member;
        }
        if (opcode.endsWith("RETURN")) {
            return "return";
        }
        if (opcode.matches("ICONST_M1")) {
            return "-1";
        }
        if (opcode.matches("[ILFD]CONST_\\d")) {
            return opcode.substring(opcode.length() - 1);
        }
        if (opcode.equals("BIPUSH") || opcode.equals("SIPUSH")) {
            return operand;
        }
        if (opcode.equals("LDC") && NUMBER.matcher(operand).matches()) {
            return withoutSuffix(operand);
        }
        if (opcode.equals("ACONST_NULL")) {
            return "null";
        }
        String operator = OPERATORS.get(opcode.length() > 1 ? opcode.substring(1) : opcode);
        if (operator != null && "ILFD".indexOf(opcode.charAt(0)) >= 0) {
            return operator;
        }
        if (opcode.matches("[ILFD]2[ILFDBCS]")) {
            return CAST_TYPES.get(opcode.charAt(2));
        }
        if (opcode.matches("[IALFDBCS]ALOAD") || opcode.matches("[IALFDBCS]ASTORE")) {
            return "[";
        }
        return switch (opcode) {
            case "ATHROW" -> "throw";
            case "NEW", "NEWARRAY", "ANEWARRAY", "MULTIANEWARRAY" -> "new";
            case "INSTANCEOF" -> "instanceof";
            case "ARRAYLENGTH" -> "length";
            case "CHECKCAST" -> operand.substring(operand.lastIndexOf('/') + 1);
            default -> opcode.startsWith("IF") ? "if" : null;
        };
    }

    private static List<Range> instructions(String bytecode, Range region) {
        List<Range> found = new ArrayList<>();
        Matcher instruction = INSTRUCTION.matcher(bytecode);
        instruction.region(region.start(), region.end());
        while (instruction.find()) {
            String opcode = instruction.group(1);
            if (!NOT_INSTRUCTIONS.contains(opcode) && !opcode.matches("L\\d+")) {
                found.add(new Range(instruction.start(1), instruction.end()));
            }
        }
        return found;
    }

    private static Range instructionAt(String bytecode, int offset) {
        Range line = lineRange(bytecode, lineIndex(bytecode, offset));
        List<Range> found = instructions(bytecode, line);
        return found.isEmpty() ? null : found.getFirst();
    }

    // The source line number in force at an offset: the last LINENUMBER between the method's header and it.
    private static int sourceLineAt(String bytecode, int methodStart, int offset) {
        Matcher number = LINE_NUMBER.matcher(bytecode);
        number.region(methodStart, Math.min(bytecode.length(), lineEnd(bytecode, offset)));
        int line = -1;
        while (number.find()) {
            line = Integer.parseInt(number.group(1));
        }
        return line;
    }

    // The stretch of bytecode an offset's source line covers: from its LINENUMBER to the next one.
    private static Range lineRegionAt(String bytecode, int methodStart, int methodEnd, int offset) {
        Matcher number = LINE_NUMBER.matcher(bytecode);
        number.region(methodStart, methodEnd);
        int start = methodStart;
        int end = methodEnd;
        while (number.find()) {
            if (number.start() <= offset) {
                start = number.start();
            } else {
                end = number.start();
                break;
            }
        }
        return new Range(start, end);
    }

    // Every stretch of the method's bytecode that comes from one of the source lines.
    private static List<Range> lineRegions(String bytecode, int methodStart, int methodEnd, List<Integer> lines) {
        List<Range> regions = new ArrayList<>();
        if (lines.isEmpty()) {
            return regions;
        }
        Matcher number = LINE_NUMBER.matcher(bytecode);
        number.region(methodStart, methodEnd);
        int start = -1;
        while (number.find()) {
            if (start >= 0) {
                regions.add(new Range(start, number.start()));
                start = -1;
            }
            if (lines.contains(Integer.parseInt(number.group(1)))) {
                start = number.start();
            }
        }
        if (start >= 0) {
            regions.add(new Range(start, methodEnd));
        }
        return regions;
    }

    private static int lineIndex(String text, int offset) {
        int index = 0;
        for (int at = 0; at < Math.min(offset, text.length()); at++) {
            if (text.charAt(at) == '\n') {
                index++;
            }
        }
        return index;
    }

    private static Range lineRange(String text, int index) {
        int start = 0;
        for (int line = 0; line < index && start >= 0; line++) {
            start = text.indexOf('\n', start);
            start = start < 0 ? -1 : start + 1;
        }
        start = Math.max(0, start);
        return new Range(start, lineEnd(text, start));
    }

    private static int lineEnd(String text, int offset) {
        int end = text.indexOf('\n', Math.min(offset, text.length()));
        return end < 0 ? text.length() : end;
    }

    private static Range trimmed(String text, Range range) {
        int start = range.start();
        int end = range.end();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new Range(start, end);
    }

    // A name or a number at an offset, such as "var1", "getCodeBase" or "-38".
    private static Range tokenAt(String text, int offset) {
        Range word = wordAt(text, offset);
        if (word != null) {
            return word;
        }
        Range operator = operatorAt(text, offset);
        if (operator != null) {
            return operator;
        }
        int start = Math.min(offset, text.length());
        while (start > 0 && (Character.isDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '.')) {
            start--;
        }
        int end = Math.min(offset, text.length());
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '.')) {
            end++;
        }
        if (start == end || !Character.isDigit(text.charAt(start))) {
            return null;
        }
        if (start > 0 && text.charAt(start - 1) == '-') {
            start--;
        }
        return new Range(start, end);
    }

    /**
     * The method whose bytecode contains {@code offset}: the last method header at or before it.
     */
    static Optional<MethodNode> methodAtBytecode(ClassNode owner, String bytecode, int offset) {
        MethodNode found = null;
        for (Header header : headers(owner, bytecode).inOrder()) {
            if (header.lineStart() > offset) {
                break;
            }
            found = header.method();
        }
        return Optional.ofNullable(found);
    }

    /**
     * The method whose Java declaration is the last one at or before {@code offset}.
     */
    static Optional<MethodNode> methodAtJava(ClassNode owner, String java, int offset) {
        return enclosing(javaMethods(owner, java), offset).map(JavaMethod::method);
    }

    /**
     * Where {@code method} is declared in the Java, as the start and end of its name, or empty if it is not found.
     */
    static Optional<int[]> javaDeclaration(ClassNode owner, MethodNode method, String java) {
        return javaMethods(owner, java).stream()
            .filter(candidate -> candidate.method() == method)
            .findFirst()
            .map(candidate -> new int[]{candidate.name().start(), candidate.name().end()});
    }

    // A method's declaration in the Java: where its line starts and where its name is.
    private record JavaMethod(MethodNode method, int start, Range name) {
    }

    private static List<JavaMethod> javaMethods(ClassNode owner, String java) {
        JavaMethods cached = lastJavaMethods;
        if (cached != null && cached.owner() == owner && cached.java().equals(java)) {
            return cached.methods();
        }
        List<JavaMethod> methods = findJavaMethods(owner, java);
        lastJavaMethods = new JavaMethods(owner, java, methods);
        return methods;
    }

    private static List<JavaMethod> findJavaMethods(ClassNode owner, String java) {
        List<JavaMethod> methods = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(java);
        while (declaration.find()) {
            if (STATEMENTS.contains(declaration.group(1))) {
                continue;
            }
            String name = declaration.group(1).equals(simpleName(owner)) ? "<init>" : declaration.group(1);
            int parameters = count(declaration.group(2));
            owner.methods.stream()
                .filter(method -> method.name.equals(name))
                .filter(method -> Type.getArgumentTypes(method.desc).length == parameters)
                .filter(method -> methods.stream().noneMatch(found -> found.method() == method))
                .findFirst()
                .ifPresent(method -> methods.add(new JavaMethod(method, declaration.start(),
                    new Range(declaration.start(1), declaration.end(1)))));
        }
        Matcher staticBlock = STATIC_BLOCK.matcher(java);
        if (staticBlock.find()) {
            owner.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst()
                .ifPresent(method -> methods.add(new JavaMethod(method, staticBlock.start(),
                    new Range(staticBlock.start(), staticBlock.end()))));
        }
        methods.sort((left, right) -> Integer.compare(left.start(), right.start()));
        return List.copyOf(methods);
    }

    // The method a Java offset is in: the last declaration before it, unless the offset is on a field line between
    // methods, which belongs to the class.
    private static Optional<JavaMethod> enclosing(List<JavaMethod> methods, int offset) {
        JavaMethod found = null;
        for (JavaMethod method : methods) {
            if (method.start() <= offset) {
                found = method;
            }
        }
        return Optional.ofNullable(found);
    }

    private static Optional<FieldNode> javaFieldAt(ClassNode owner, String java, int offset) {
        int start = lineStart(java, offset);
        int end = java.indexOf('\n', offset);
        Matcher field = FIELD.matcher(java.substring(start, end < 0 ? java.length() : end));
        if (!field.find()) {
            return Optional.empty();
        }
        String name = field.group(1);
        return owner.fields.stream().filter(candidate -> candidate.name.equals(name)).findFirst();
    }

    private static Optional<Range> javaField(ClassNode owner, FieldNode field, String java) {
        List<JavaMethod> methods = javaMethods(owner, java);
        int firstMethod = methods.isEmpty() ? java.length() : methods.getFirst().start();
        Matcher declaration = FIELD.matcher(java);
        while (declaration.find() && declaration.start() < firstMethod) {
            if (declaration.group(1).equals(field.name)) {
                return Optional.of(new Range(declaration.start(1), declaration.end(1)));
            }
        }
        return Optional.empty();
    }

    // Bytecode declares a field as its descriptor and name, such as "static I count".
    private static Optional<Range> bytecodeField(FieldNode field, String bytecode) {
        Matcher declaration = Pattern.compile("(?m)^[ \\t]*(?:[a-z]+ )*" + Pattern.quote(field.desc + " " + field.name)
            + "\\b").matcher(bytecode);
        if (!declaration.find()) {
            return Optional.empty();
        }
        return Optional.of(new Range(declaration.end() - field.name.length(), declaration.end()));
    }

    private static Optional<FieldNode> bytecodeFieldAt(ClassNode owner, String bytecode, int offset) {
        int line = lineStart(bytecode, offset);
        for (FieldNode field : owner.fields) {
            Optional<Range> declaration = bytecodeField(field, bytecode);
            if (declaration.isPresent() && lineStart(bytecode, declaration.get().start()) == line) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    // A method header is its name and descriptor at the start of a line, after any modifiers.
    private static Optional<Range> bytecodeHeader(ClassNode owner, MethodNode method, String bytecode) {
        return Optional.ofNullable(headers(owner, bytecode).names().get(method));
    }

    // Where the next method's header line starts, or the end of the text.
    private static int bytecodeMethodEnd(ClassNode owner, String bytecode, int start) {
        for (Header header : headers(owner, bytecode).inOrder()) {
            if (header.name().start() > start) {
                return header.lineStart();
            }
        }
        return bytecode.length();
    }

    // Every method's header, found in one pass: a large class has hundreds of methods and megabytes of bytecode,
    // and a link is made on every caret move. The last texts' are kept, since the same class is clicked again.
    private static Headers headers(ClassNode owner, String bytecode) {
        Headers cached = lastHeaders;
        if (cached != null && cached.owner() == owner && cached.bytecode().equals(bytecode)) {
            return cached;
        }
        Map<String, MethodNode> bySignature = new HashMap<>();
        for (MethodNode method : owner.methods) {
            bySignature.putIfAbsent(method.name + method.desc, method);
        }
        Map<MethodNode, Range> names = new IdentityHashMap<>();
        List<Header> inOrder = new ArrayList<>();
        int lineStart = 0;
        while (lineStart < bytecode.length()) {
            int lineEnd = lineEnd(bytecode, lineStart);
            int at = lineStart;
            while (at < lineEnd && (bytecode.charAt(at) == ' ' || bytecode.charAt(at) == '\t')) {
                at++;
            }
            // Modifiers are lower-case words, each followed by a space.
            int word = at;
            while (word < lineEnd && bytecode.charAt(word) >= 'a' && bytecode.charAt(word) <= 'z') {
                word++;
                if (word < lineEnd && bytecode.charAt(word) == ' ') {
                    at = ++word;
                }
            }
            int end = at;
            while (end < lineEnd && !Character.isWhitespace(bytecode.charAt(end))) {
                end++;
            }
            int parameters = at;
            while (parameters < end && bytecode.charAt(parameters) != '(') {
                parameters++;
            }
            if (parameters > at && parameters < end) {
                MethodNode method = bySignature.get(bytecode.substring(at, end));
                if (method != null && !names.containsKey(method)) {
                    Range name = new Range(at, parameters);
                    names.put(method, name);
                    inOrder.add(new Header(method, name, lineStart));
                }
            }
            lineStart = lineEnd + 1;
        }
        Headers headers = new Headers(owner, bytecode, names, List.copyOf(inOrder));
        lastHeaders = headers;
        return headers;
    }

    private record Header(MethodNode method, Range name, int lineStart) {
    }

    private record Headers(ClassNode owner, String bytecode, Map<MethodNode, Range> names, List<Header> inOrder) {
    }

    private record JavaMethods(ClassNode owner, String java, List<JavaMethod> methods) {
    }

    // Links are made on the event thread; these only save work, so a lost update costs nothing.
    private static volatile Headers lastHeaders;
    private static volatile JavaMethods lastJavaMethods;

    /**
     * The name at {@code offset} in {@code source}, found again in {@code target} between {@code targetStart} and
     * {@code targetEnd}: the occurrence with the same count from the start of the method, or the last one if the
     * target has fewer.
     */
    private static Optional<Range> sameOccurrence(String source, int sourceStart, int offset, String target,
                                                  int targetStart, int targetEnd) {
        Range word = wordAt(source, offset);
        if (word == null || word.start() < sourceStart) {
            return Optional.empty();
        }
        String name = source.substring(word.start(), word.end());
        int count = occurrences(source, name, sourceStart, word.end()).size();
        List<Range> found = occurrences(target, name, targetStart, targetEnd);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(Math.min(count, found.size()) - 1));
    }

    // A conditional jump reads as any of the statements that branch.
    private static final List<String> BRANCHES = List.of("if", "while", "for");

    private static List<Range> occurrences(String text, String name, int start, int end) {
        List<Range> found = new ArrayList<>();
        String pattern;
        if (name.equals("if")) {
            pattern = "(?<![\\w$])(?:" + String.join("|", BRANCHES) + ")(?![\\w$])|\\?";
        } else if (OPERATOR_PATTERNS.containsKey(name)) {
            pattern = OPERATOR_PATTERNS.get(name);
        } else if (UNSUFFIXED_NUMBER.matcher(name).matches()) {
            // With any suffix Java gives it, and not the end of a longer number such as 1.5.
            pattern = "(?<![\\w$.])" + Pattern.quote(name) + "[LFDlfd]?(?![\\w$.])";
        } else {
            pattern = "(?<![\\w$])" + Pattern.quote(name) + "(?![\\w$])";
        }
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        matcher.region(start, end);
        while (matcher.find()) {
            found.add(new Range(matcher.start(), matcher.end()));
        }
        return found;
    }

    private static final String OPERATOR_CHARACTERS = "+-*/%&|^<>?[";

    // An operator at an offset: the run of operator characters around it, such as "*", ">>>" or "+=".
    private static Range operatorAt(String text, int offset) {
        int at = Math.min(offset, text.length());
        if (at < text.length() && OPERATOR_CHARACTERS.indexOf(text.charAt(at)) >= 0) {
            // The click is on the operator itself.
        } else if (at > 0 && OPERATOR_CHARACTERS.indexOf(text.charAt(at - 1)) >= 0) {
            at--;
        } else {
            return null;
        }
        if (text.charAt(at) == '?' || text.charAt(at) == '[') {
            return new Range(at, at + 1);
        }
        int start = at;
        while (start > 0 && "+-*/%&|^<>".indexOf(text.charAt(start - 1)) >= 0) {
            start--;
        }
        int end = at + 1;
        while (end < text.length() && "+-*/%&|^<>=".indexOf(text.charAt(end)) >= 0) {
            end++;
        }
        return new Range(start, end);
    }

    /**
     * The token an instruction would have for a clicked piece of Java: a compound assignment such as {@code +=} is
     * its operator, and every branching statement is {@code if}.
     */
    private static String instructionToken(String clicked) {
        if (BRANCHES.contains(clicked) || clicked.equals("?")) {
            return "if";
        }
        if (clicked.length() > 1 && clicked.endsWith("=") && OPERATORS.containsValue(clicked.substring(0,
            clicked.length() - 1))) {
            return clicked.substring(0, clicked.length() - 1);
        }
        return withoutSuffix(clicked);
    }

    private static String withoutSuffix(String number) {
        Matcher suffixed = SUFFIXED_NUMBER.matcher(number);
        return suffixed.matches() ? suffixed.group(1) : number;
    }

    private static Range wordAt(String text, int offset) {
        int start = Math.min(offset, text.length());
        while (start > 0 && isNamePart(text.charAt(start - 1))) {
            start--;
        }
        int end = Math.min(offset, text.length());
        while (end < text.length() && isNamePart(text.charAt(end))) {
            end++;
        }
        if (start == end || Character.isDigit(text.charAt(start))) {
            return null;
        }
        return new Range(start, end);
    }

    private static boolean isNamePart(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    private static int lineStart(String text, int offset) {
        return text.lastIndexOf('\n', Math.max(0, Math.min(offset, text.length()) - 1)) + 1;
    }

    private static int count(String parameters) {
        return parameters.isBlank() ? 0 : parameters.split(",").length;
    }

    private static String simpleName(ClassNode owner) {
        String name = owner.name.substring(owner.name.lastIndexOf('/') + 1);
        return name.substring(name.lastIndexOf('$') + 1);
    }
}
