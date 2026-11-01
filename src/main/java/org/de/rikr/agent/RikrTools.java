package org.de.rikr.agent;

import org.de.rikr.Logger;
import org.de.rikr.decompiler.DecompileResult;
import org.de.rikr.flow.CallGraph;
import org.de.rikr.flow.ControlFlowBuilder;
import org.de.rikr.flow.ControlFlowGraph;
import org.de.rikr.flow.EntryPoint;
import org.de.rikr.flow.MethodRef;
import org.de.rikr.jar.JarExportService;
import org.de.rikr.mapping.ClassMappingDecision;
import org.de.rikr.mapping.ClassMatch;
import org.de.rikr.mapping.ClassVoting;
import org.de.rikr.mapping.MemberMatch;
import org.de.rikr.mapping.MemberMatches;
import org.de.rikr.mapping.format.MappingFormat;
import org.de.rikr.mapping.format.MappingReader;
import org.de.rikr.mapping.format.NameMappings;
import org.de.rikr.model.ClassRef;
import org.de.rikr.project.JarOperations;
import org.de.rikr.project.Project;
import org.de.rikr.project.ProjectLayout;
import org.de.rikr.project.ProjectSession;
import org.de.rikr.project.ProjectSessionService;
import org.de.rikr.project.RenameOperations;
import org.de.rikr.reference.ReferenceResult;
import org.de.rikr.rename.NamingWorklist;
import org.de.rikr.rename.RenameBatch;
import org.de.rikr.rename.RenameOperation;
import org.de.rikr.rename.RenamePreview;
import org.de.rikr.rename.RenameResult;
import org.de.rikr.rename.RenameStep;
import org.de.rikr.rename.RenameUndoResult;
import org.de.rikr.search.SearchQuery;
import org.de.rikr.search.SearchResult;
import org.de.rikr.settings.DecompilerCacheSettings;
import org.de.rikr.symbol.BytecodeText;
import org.de.rikr.symbol.Declaration;
import org.de.rikr.symbol.Symbol;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Rikr's features as MCP tools, working on one headless project. Tools run one at a time on the server's thread,
 * which is the only thread that changes the project, just as the event thread is in the GUI.
 */
final class RikrTools implements AutoCloseable {
    private static final int DEFAULT_LIMIT = 200;
    private static final int DEFAULT_MAX_CHARS = 60_000;
    private static final long DECOMPILE_TIMEOUT_SECONDS = 120;

    private final ProjectSessionService sessions = new ProjectSessionService();
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    // Only messages from the thread running a tool belong in its result; the decompiler's own thread logs a great
    // deal that has nothing to do with the call.
    private volatile Thread toolThread;
    private final Logger logger;
    private Project project;
    private File projectFile;
    private ProjectLayout layout = ProjectLayout.DEFAULT;

    RikrTools(Logger diagnostics) {
        this.logger = message -> {
            diagnostics.log(message);
            if (Thread.currentThread() == toolThread) {
                log.add(message);
            }
        };
        this.project = new Project(logger);
        configureDecompilerCache();
    }

    // The same cache as the GUI's, from Rikr's settings file.
    private void configureDecompilerCache() {
        DecompilerCacheSettings settings = DecompilerCacheSettings.load();
        project.decompiler().configureDiskCache(settings.directoryPath(), settings.maxSizeBytes());
    }

    @Override
    public void close() {
        project.close();
    }

    List<Tool> tools() {
        return List.of(
            tool("load_jars", """
                    Load JAR or .class files into the project. JARs are identified by file name, so a second JAR \
                    with the same name is skipped. Loose .class files share the group "Classes".""",
                Schema.object().stringArray("paths", "Absolute paths of .jar or .class files.", true),
                false, this::loadJars),
            tool("remove_jar", "Remove a loaded JAR, with its renames, class mappings and bookmarks.",
                Schema.object().string("jar", "JAR name, as list_jars shows it.", true),
                false, this::removeJar),
            tool("list_jars", "List the loaded JARs with their class counts, source files and unsaved renames.",
                Schema.object(), true, arguments -> listJars()),
            tool("list_classes", """
                    List a JAR's classes by internal name (a/b/C), sorted. Use filter to narrow by substring and \
                    offset/limit to page.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("filter", "Only classes whose name contains this text (case-insensitive).", false)
                    .integer("offset", "Index of the first class to return. Default 0.")
                    .integer("limit", "How many classes to return. Default 200."),
                true, this::listClasses),
            tool("describe_class",
                "Show a class's access, superclass, interfaces, fields and methods with descriptors.",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name, internal (a/b/C) or dotted (a.b.C).", true),
                true, this::describeClass),
            tool("get_bytecode", """
                    Print a class as ASM bytecode text, as the GUI's bytecode view shows it. Give method (and \
                    descriptor if overloaded) to print only that method.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name.", true)
                    .string("method", "Only print methods with this name.", false)
                    .string("descriptor", "Only print the method with this descriptor, such as (I)V.", false)
                    .integer("max_chars", "Truncate longer output. Default 60000."),
                true, this::bytecode),
            tool("list_entry_points", """
                    Where a JAR's code starts, most likely first: the manifest's Main-Class, static main methods, \
                    applet lifecycles (init, then start) even in obfuscated applets, thread run() methods and \
                    event listener methods. Start a call graph from one of these.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .integer("limit", "How many entry points to return. Default 200."),
                true, this::entryPoints),
            tool("naming_progress", """
                    How many of a JAR's classes have names a person gave, and the classes still named by the \
                    obfuscator, most referenced first: the ones whose names make the most code readable. Counts \
                    only classes in no package when the JAR has any, since those are the program's own.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .integer("limit", "How many unnamed classes to return. Default 200."),
                true, this::namingProgress),
            tool("get_call_graph", """
                    The methods a method calls, and what those call, breadth first to a depth. Virtual and \
                    interface calls also list the overrides in the JAR they may dispatch to. Methods at the \
                    depth limit are listed as targets only.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class of the starting method.", true)
                    .string("method", "Starting method name.", true)
                    .string("descriptor", "Method descriptor, needed when the name is overloaded.", false)
                    .integer("depth", "How many calls deep to follow. Default 2, at most 6.")
                    .bool("include_library",
                        "Also list calls to methods outside the JAR, such as the JDK. Default false.")
                    .integer("limit", "Most methods to expand. Default 200."),
                true, this::callGraph),
            tool("get_control_flow", """
                    The control flow graph of a method: its basic blocks of bytecode, named by the labels the \
                    bytecode view prints, and the edges between them (fall-through, jump, branch taken or not, \
                    switch case, exception handler). Unreachable blocks are marked.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name.", true)
                    .string("method", "Method name.", true)
                    .string("descriptor", "Method descriptor, needed when the name is overloaded.", false)
                    .integer("max_chars", "Truncate longer output. Default 60000."),
                true, this::controlFlow),
            tool("decompile",
                "Decompile a class to Java with the JetBrains (FernFlower) decompiler, using current names.",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name.", true)
                    .integer("max_chars", "Truncate longer output. Default 60000."),
                true, this::decompile),
            tool("search", """
                    Search every loaded JAR's class, field and method names and instructions, as the GUI's global \
                    search does. The text needs at least 2 characters.""",
                Schema.object()
                    .string("text", "Text to find.", true)
                    .bool("match_case", "Match upper and lower case exactly. Default false.")
                    .bool("match_word", "Match whole words only. Default false.")
                    .integer("limit", "How many result lines to return. Default 200."),
                true, this::search),
            tool("find_references", """
                    Find references within a JAR. For a class: where its type is used. For a field: reads and \
                    writes. For a method: callers and callees.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name.", true)
                    .string("field", "Field name, for field references.", false)
                    .string("method", "Method name, for method references.", false)
                    .string("descriptor", "Member descriptor, needed when the name is overloaded.", false)
                    .integer("limit", "How many references to return per kind. Default 200."),
                true, this::findReferences),
            tool("rename", """
                    Rename a class, or a field or method of a class, everywhere in its JAR. Overriding methods are \
                    renamed with it. Use dry_run to see what would change. Renames are recorded so undo and \
                    reopening a saved project replay them.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("class", "Class name; the class to rename, or the member's class.", true)
                    .string("field", "Field to rename.", false)
                    .string("method", "Method to rename.", false)
                    .string("descriptor", "Member descriptor, needed when the name is overloaded.", false)
                    .string("new_name", "New name. For a class, an internal name such as game/Player.", true)
                    .bool("dry_run", "Only report what would change. Default false."),
                false, this::rename),
            tool("undo", "Undo the last rename, or the last mapping import as a whole.",
                Schema.object(), false, arguments -> undo()),
            tool("redo", "Apply again the rename or import undone last. Renaming anything else first forgets it.",
                Schema.object(), false, arguments -> redo()),
            tool("rename_history",
                "List the recorded renames and imports, oldest first, and the JARs with unsaved renames.",
                Schema.object(), true, arguments -> history()),
            tool("import_mappings", """
                    Apply a tiny v2, Enigma or ProGuard mapping file to a JAR as one undoable step. The file must \
                    name classes and members as they were when the JAR was loaded. Entries not in the JAR are \
                    skipped. Use dry_run to see what would change and any problems.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("path", "Absolute path of the mapping file.", true)
                    .bool("dry_run", "Only report what would change. Default false."),
                false, this::importMappings),
            tool("export_mappings", """
                    Write a JAR's renames, from the names it was loaded with to its current names, as a mapping \
                    file.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("path", "Absolute path of the file to write.", true)
                    .choice("format", "Mapping format.", true, "tiny", "enigma", "proguard"),
                false, this::exportMappings),
            tool("export_jar", """
                    Write a JAR with its current names to a new file, keeping the original's resources and manifest. \
                    Signature files are left out because the classes changed.""",
                Schema.object()
                    .string("jar", "JAR name.", true)
                    .string("path", "Absolute path of the JAR to write.", true),
                false, this::exportJar),
            tool("overwrite_original_jar", """
                    Replace the JAR file a JAR was loaded from with its current names. The previous file is kept \
                    next to it as <name>.bak. The JAR's renames then leave the history.""",
                Schema.object().string("jar", "JAR name.", true),
                false, this::overwriteOriginalJar),
            tool("match_classes", """
                    Rank the classes of a target JAR by structural similarity to a source class, for mapping \
                    classes between two versions of a program.""",
                Schema.object()
                    .string("source_jar", "JAR of the class to match.", true)
                    .string("class", "Class to match.", true)
                    .string("target_jar", "JAR to search.", true)
                    .integer("min_similarity", "Lowest similarity percentage to list. Default 50.")
                    .integer("limit", "How many candidates to return. Default 20."),
                true, this::matchClasses),
            tool("accept_class_mapping", "Record that a source class corresponds to a target class in another JAR.",
                Schema.object()
                    .string("source_jar", "Source JAR.", true)
                    .string("class", "Source class.", true)
                    .string("target_jar", "Target JAR.", true)
                    .string("target_class", "Target class.", true),
                false, this::acceptClassMapping),
            tool("auto_match_classes", """
                    Accept every source class whose best match in the target JAR is unique and at least \
                    min_similarity percent similar in shape. Then, unless use_members is false, match more classes \
                    from their members: matched fields and methods vote for the classes of their types, which \
                    works where a deobfuscator changed the classes' shape.""",
                Schema.object()
                    .string("source_jar", "Source JAR.", true)
                    .string("target_jar", "Target JAR.", true)
                    .integer("min_similarity", "Lowest similarity percentage to accept. Default 90.")
                    .bool("use_members", "Also match classes from their members. Default true; slower."),
                false, this::autoMatch),
            tool("match_members", """
                    Match the fields and methods of the source JAR to the target JAR's, within the accepted class \
                    mappings between them, by what their code does and how they are used. Static members are \
                    matched across the whole JAR, since obfuscators move them between classes. Ties are left \
                    unmatched. Changes nothing.""",
                Schema.object()
                    .string("source_jar", "Source JAR.", true)
                    .string("target_jar", "Target JAR.", true)
                    .integer("limit", "How many matches to list. Default 200."),
                true, this::matchMembers),
            tool("carry_names", """
                    Give the classes, fields and methods of the target JAR the names given to their matches in the \
                    source JAR, as one rename that undo reverts. Uses the accepted class mappings and match_members. \
                    Only names given since the source JAR was loaded are carried, unless include_loaded_names is set. \
                    Targets that were renamed already keep their names. Renames that can't be made, such as two \
                    matches carrying one name into a class, are left out.""",
                Schema.object()
                    .string("source_jar", "JAR whose names are carried, usually the older version.", true)
                    .string("target_jar", "JAR that gets the names.", true)
                    .bool("include_loaded_names", """
                        Also carry the names the source JAR was loaded with, for a JAR named before loading, such \
                        as a deobfuscated build. Made-up names such as class12 or field34 are not carried.""")
                    .bool("dry_run", "Only report what would be renamed."),
                false, this::carryNames),
            tool("list_class_mappings", "List the accepted class mappings between JARs.",
                Schema.object(), true, arguments -> listClassMappings()),
            tool("open_project", "Open a saved .rikr project: its JARs, renames, class mappings and bookmarks.",
                Schema.object().string("path", "Absolute path of the .rikr file.", true),
                false, this::openProject),
            tool("save_project", """
                    Save the project: JAR paths, renames, class mappings and bookmarks. The GUI can open it. \
                    Without a path, saves to the file the project was opened from or last saved to.""",
                Schema.object().string("path", "Absolute path of the .rikr file to write.", false),
                false, this::saveProject)
        );
    }

    private Tool tool(String name, String description, Schema schema, boolean readOnly, Tool.Handler handler) {
        return new Tool(name, description, schema.build(), readOnly, arguments -> {
            log.clear();
            toolThread = Thread.currentThread();
            String text;
            try {
                text = handler.call(arguments);
            } finally {
                toolThread = null;
            }
            List<String> messages;
            synchronized (log) {
                messages = List.copyOf(log);
                log.clear();
            }
            return messages.isEmpty() ? text : text + "\n\nLog:\n" + String.join("\n", messages);
        });
    }

    private String loadJars(Arguments arguments) throws ToolException {
        List<File> jars = new ArrayList<>();
        List<File> classFiles = new ArrayList<>();
        for (String path : arguments.strings("paths")) {
            File file = new File(path);
            if (!file.isFile()) {
                throw new ToolException("No such file: " + path);
            }
            if (path.endsWith(".jar")) {
                jars.add(file);
            } else if (path.endsWith(".class")) {
                classFiles.add(file);
            } else {
                throw new ToolException("Not a .jar or .class file: " + path);
            }
        }
        JarOperations operations = project.jars();
        List<String> added = operations.addJars(operations.readJars(jars));
        operations.addClassFiles(operations.readClassFiles(classFiles));
        project.warmUpDecompiler(added);
        return "Loaded: " + (added.isEmpty() && classFiles.isEmpty() ? "nothing new" : String.join(", ", added))
            + "\n\n" + listJars();
    }

    private String removeJar(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        project.jars().removeJar(jar);
        return "Removed " + jar + ".";
    }

    private String listJars() {
        Map<String, List<ClassNode>> jars = project.jars().classesByJar();
        if (jars.isEmpty()) {
            return "No JARs are loaded.";
        }
        StringBuilder out = new StringBuilder();
        Set<String> dirty = project.renames().dirtyJars();
        jars.keySet().stream().sorted().forEach(jar -> {
            File source = project.jars().sourceFile(jar);
            out.append(jar).append(": ").append(jars.get(jar).size()).append(" classes");
            if (source != null) {
                out.append(", from ").append(source.getAbsolutePath());
            }
            if (dirty.contains(jar)) {
                out.append(", unsaved renames");
            }
            out.append('\n');
        });
        return out.toString().strip();
    }

    private String listClasses(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        String filter = arguments.optionalString("filter");
        Predicate<String> matches = filter == null ? name -> true
            : name -> name.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
        List<ClassNode> classes = project.jars().classes(jar).stream()
            .filter(classNode -> matches.test(classNode.name))
            .sorted(Comparator.comparing(classNode -> classNode.name))
            .toList();
        int offset = Math.max(0, arguments.integer("offset", 0));
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        StringBuilder out = new StringBuilder();
        out.append(classes.size()).append(" classes");
        if (offset > 0 || classes.size() > limit) {
            out.append(", showing ").append(Math.min(offset, classes.size())).append(" to ")
                .append(Math.min(offset + limit, classes.size()));
        }
        out.append('\n');
        int from = Math.min(offset, classes.size());
        for (ClassNode classNode : classes.subList(from, Math.min(offset + limit, classes.size()))) {
            out.append(classNode.name).append("  (").append(classNode.fields.size()).append(" fields, ")
                .append(classNode.methods.size()).append(" methods)\n");
        }
        return out.toString().strip();
    }

    private String describeClass(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        StringBuilder out = new StringBuilder();
        out.append(Modifier.toString(classNode.access & Modifier.classModifiers())).append(' ')
            .append(kind(classNode)).append(' ').append(classNode.name).append('\n');
        String original = project.jars().originalName(classNode);
        if (!original.equals(classNode.name)) {
            out.append("Loaded as: ").append(original).append('\n');
        }
        out.append("Superclass: ").append(classNode.superName).append('\n');
        out.append("Interfaces: ")
            .append(classNode.interfaces.isEmpty() ? "none" : String.join(", ", classNode.interfaces))
            .append('\n');
        out.append("\nFields (").append(classNode.fields.size()).append("):\n");
        for (FieldNode field : classNode.fields) {
            out.append("  ").append(modifiers(field.access)).append(field.name).append(' ').append(field.desc)
                .append('\n');
        }
        out.append("\nMethods (").append(classNode.methods.size()).append("):\n");
        for (MethodNode method : classNode.methods) {
            out.append("  ").append(modifiers(method.access)).append(method.name).append(method.desc).append('\n');
        }
        return out.toString().strip();
    }

    private String bytecode(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        String method = arguments.optionalString("method");
        String descriptor = arguments.optionalString("descriptor");
        ClassNode printed = classNode;
        if (method != null) {
            List<MethodNode> methods = classNode.methods.stream()
                .filter(candidate -> candidate.name.equals(method))
                .filter(candidate -> descriptor == null || candidate.desc.equals(descriptor))
                .toList();
            if (methods.isEmpty()) {
                throw new ToolException(classNode.name + " declares no method " + method
                    + (descriptor == null ? "" : descriptor) + ". Methods: " + memberList(classNode.methods));
            }
            // Print a copy holding only the chosen methods; the class itself is not changed.
            printed = new ClassNode();
            printed.version = classNode.version;
            printed.access = classNode.access;
            printed.name = classNode.name;
            printed.superName = classNode.superName;
            printed.interfaces = classNode.interfaces;
            printed.methods = methods;
        }
        return truncate(BytecodeText.of(printed).text(), arguments);
    }

    private String entryPoints(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        List<EntryPoint> entries = project.jars().entryPoints(jar);
        if (entries.isEmpty()) {
            return "No entry points found in " + jar + ".";
        }
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        StringBuilder out = new StringBuilder(entries.size() + " entry points in " + jar
            + (entries.size() > limit ? ", showing " + limit : "") + ", most likely first:\n");
        for (EntryPoint entry : entries.subList(0, Math.min(limit, entries.size()))) {
            out.append("  ").append(entry.kind().name().toLowerCase(Locale.ROOT)).append(": ").append(entry.method())
                .append("  (").append(entry.reason()).append(")\n");
        }
        return out.toString().strip();
    }

    private String namingProgress(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        NamingWorklist.Progress progress = NamingWorklist.of(project.jars().classes(jar),
            project.jars()::originalName, classNode -> project.references().findTypeReferences(jar, classNode).size(),
            limit);
        StringBuilder out = new StringBuilder(progress.named() + " of " + progress.classes() + " classes named in "
            + jar + ".");
        if (progress.next().isEmpty()) {
            return out.append(" No class has an obfuscated name.").toString();
        }
        out.append(" Unnamed, most referenced first:\n");
        for (NamingWorklist.Entry entry : progress.next()) {
            out.append("  ").append(entry.classNode().name).append("  (").append(entry.references())
                .append(entry.references() == 1 ? " reference)\n" : " references)\n");
        }
        return out.toString().strip();
    }

    private String callGraph(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        if (!(member(arguments, classNode, jar) instanceof Declaration.MethodDeclaration declaration)) {
            throw new ToolException("Give the method to start the call graph from.");
        }
        int depth = Math.max(1, Math.min(6, arguments.integer("depth", 2)));
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        boolean library = arguments.bool("include_library", false);
        CallGraph graph = new CallGraph(project.jars().classes(jar));
        MethodRef root = new MethodRef(declaration.owner().name, declaration.method().name, declaration.method().desc);
        Map<MethodRef, List<CallGraph.Call>> reachable = graph.reachable(root, depth, limit, library);
        StringBuilder out = new StringBuilder("Calls from " + root + ", " + depth + " deep: " + reachable.size()
            + " methods expanded\n");
        for (Map.Entry<MethodRef, List<CallGraph.Call>> entry : reachable.entrySet()) {
            out.append('\n').append(entry.getKey()).append(entry.getValue().isEmpty() ? " (calls nothing shown)" : "")
                .append('\n');
            for (CallGraph.Call call : entry.getValue()) {
                out.append("  -> ").append(call.kind().name().toLowerCase(Locale.ROOT)).append(' ')
                    .append(call.target()).append(call.inJar() ? "" : "  [outside the JAR]").append('\n');
            }
        }
        return truncate(out.toString().strip(), arguments);
    }

    private String controlFlow(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        if (!(member(arguments, classNode, jar) instanceof Declaration.MethodDeclaration declaration)) {
            throw new ToolException("Give the method whose control flow you want.");
        }
        ControlFlowGraph graph = ControlFlowBuilder.build(declaration.owner().name, declaration.method());
        long unreachable = graph.blocks().stream().filter(block -> !block.reachable()).count();
        StringBuilder out = new StringBuilder();
        out.append(graph.owner()).append('.').append(graph.method()).append(graph.descriptor()).append(": ")
            .append(graph.blocks().size()).append(" blocks, ").append(graph.edges().size()).append(" edges");
        if (unreachable > 0) {
            out.append(", ").append(unreachable).append(" unreachable");
        }
        out.append('\n');
        for (ControlFlowGraph.Block block : graph.blocks()) {
            out.append('\n').append(blockName(block));
            if (!block.lineNumbers().isEmpty()) {
                out.append(" (source line ").append(block.lineNumbers().getFirst())
                    .append(block.lineNumbers().size() > 1 ? "-" + block.lineNumbers().getLast() : "").append(')');
            }
            if (!block.reachable()) {
                out.append(" [unreachable]");
            }
            out.append(":\n");
            block.lines().forEach(line -> out.append("    ").append(line).append('\n'));
            List<String> successors = graph.edges().stream()
                .filter(edge -> edge.from() == block.id())
                .map(edge -> edgeKind(edge) + " -> " + blockName(graph.blocks().get(edge.to())))
                .toList();
            out.append("  ").append(successors.isEmpty() ? "exits the method" : String.join(", ", successors))
                .append('\n');
        }
        return truncate(out.toString().strip(), arguments);
    }

    private static String blockName(ControlFlowGraph.Block block) {
        return block.id() == 0 ? "entry" : block.label() != null ? block.label() : "block " + block.id();
    }

    private static String edgeKind(ControlFlowGraph.Edge edge) {
        String kind = switch (edge.kind()) {
            case FALLTHROUGH -> "next";
            case JUMP -> "jump";
            case TRUE -> "if true";
            case FALSE -> "if false";
            case SWITCH -> "case";
            case EXCEPTION -> "catch";
        };
        return edge.label() == null ? kind : kind + " " + edge.label();
    }

    private String decompile(Arguments arguments) throws Exception {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        DecompileResult result = project.decompiler().decompileClassAsync(classNode)
            .get(DECOMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!result.success()) {
            throw new ToolException("Could not decompile " + classNode.name + ": " + result.message());
        }
        return truncate(result.source(), arguments);
    }

    private String search(Arguments arguments) throws ToolException {
        SearchQuery query = new SearchQuery(arguments.string("text"), arguments.bool("match_case", false),
            arguments.bool("match_word", false));
        if (!query.isUsable()) {
            throw new ToolException("Search text needs at least 2 characters.");
        }
        List<SearchResult> results = project.search(query);
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        StringBuilder out = new StringBuilder();
        out.append(results.size()).append(" result lines").append(results.size() > limit ? ", showing " + limit : "")
            .append('\n');
        for (SearchResult result : results.subList(0, Math.min(limit, results.size()))) {
            out.repeat("  ", result.indentLevel()).append(result.icon().name().toLowerCase(Locale.ROOT))
                .append(": ").append(result.text());
            if (result.indentLevel() == 0 && result.classNode() != null) {
                project.jars().jarOf(result.classNode()).ifPresent(jar -> out.append("  [").append(jar).append(']'));
            }
            out.append('\n');
        }
        return out.toString().strip();
    }

    private String findReferences(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        int limit = Math.max(1, arguments.integer("limit", DEFAULT_LIMIT));
        var references = project.references();
        StringBuilder out = new StringBuilder();
        switch (member(arguments, classNode, jar)) {
            case Declaration.ClassDeclaration target ->
                appendReferences(out, "References to " + target.owner().name,
                    references.findTypeReferences(jar, target.owner()), limit);
            case Declaration.FieldDeclaration target -> {
                appendReferences(out, "Reads", references.findFieldReads(jar, target.owner(), target.field()), limit);
                appendReferences(out, "Writes", references.findFieldWrites(jar, target.owner(), target.field()), limit);
            }
            case Declaration.MethodDeclaration target -> {
                appendReferences(out, "Callers",
                    references.findMethodCallers(jar, target.owner(), target.method()), limit);
                appendReferences(out, "Callees",
                    references.findMethodCallees(jar, target.owner(), target.method()), limit);
            }
        }
        return out.toString().strip();
    }

    private String rename(Arguments arguments) throws ToolException {
        String jar = jar(arguments, "jar");
        ClassNode classNode = classNode(jar, arguments.className("class"));
        Declaration declaration = member(arguments, classNode, jar);
        String newName = arguments.string("new_name");
        if (declaration instanceof Declaration.ClassDeclaration) {
            newName = newName.replace('.', '/');
        }
        RenamePreview preview = project.renames().preview(jar, declaration, newName);
        if (!preview.valid()) {
            throw new ToolException("Cannot rename " + preview.oldName() + " to " + newName + ": "
                + preview.validationMessage());
        }
        String summary = String.format("%s %s to %s: %d references in %d classes (%s).",
            arguments.bool("dry_run", false) ? "Would rename" : "Renamed", preview.oldName(), preview.newName(),
            preview.referenceCount(), preview.fileCount(), String.join(", ", limitList(preview.affectedClasses(), 30)));
        if (arguments.bool("dry_run", false)) {
            return summary;
        }
        project.renames().rename(jar, declaration, newName);
        return summary;
    }

    private String undo() throws ToolException {
        Optional<RenameUndoResult> undone = project.renames().undoLast();
        if (undone.isEmpty()) {
            throw new ToolException("There is nothing to undo.");
        }
        return "Undid " + describe(undone.get().step()) + ".";
    }

    private String redo() throws ToolException {
        Optional<RenameUndoResult> redone;
        try {
            redone = project.renames().redoLast();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ToolException("Can't redo: " + exception.getMessage());
        }
        if (redone.isEmpty()) {
            throw new ToolException("There is nothing to redo.");
        }
        return "Redid " + describe(redone.get().step()) + ".";
    }

    private String history() {
        List<RenameStep> steps = project.renames().history();
        StringBuilder out = new StringBuilder();
        out.append(steps.isEmpty() ? "No renames recorded." : steps.size() + " steps, oldest first:").append('\n');
        for (RenameStep step : steps) {
            out.append("  ").append(describe(step)).append('\n');
        }
        Set<String> dirty = project.renames().dirtyJars();
        out.append("Unsaved renames in: ")
            .append(dirty.isEmpty() ? "none" : String.join(", ", dirty.stream().sorted().toList()));
        return out.toString();
    }

    private String importMappings(Arguments arguments) throws Exception {
        String jar = jar(arguments, "jar");
        File file = new File(arguments.string("path"));
        if (!file.isFile()) {
            throw new ToolException("No such file: " + file);
        }
        NameMappings mappings = MappingReader.read(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        RenameOperations.ImportPlan plan = project.renames().planImport(jar, mappings, file.getName());
        if (!plan.problems().isEmpty()) {
            throw new ToolException(file.getName() + " cannot be applied, so nothing was renamed. "
                + plan.problems().size() + " problems:\n" + String.join("\n", limitList(plan.problems(), 50)));
        }
        RenameBatch batch = plan.batch();
        String counts = String.format(
            "%d classes, %d fields and %d methods (including overrides); %d entries are not in %s.",
            batch.classes().size(), batch.fields().size(), batch.methods().size(), plan.skipped(), jar);
        if (arguments.bool("dry_run", false) || batch.size() == 0) {
            return (batch.size() == 0 ? "Nothing to rename: " : "Would rename ") + counts;
        }
        RenameResult result = project.renames().applyImport(plan);
        return "Renamed " + counts + " Changed " + result.referenceCount() + " references in " + result.fileCount()
            + " classes. undo reverts the whole import.";
    }

    private String exportMappings(Arguments arguments) throws Exception {
        String jar = jar(arguments, "jar");
        MappingFormat format = switch (arguments.string("format")) {
            case "tiny" -> MappingFormat.TINY_V2;
            case "enigma" -> MappingFormat.ENIGMA;
            case "proguard" -> MappingFormat.PROGUARD;
            default -> throw new ToolException("format must be tiny, enigma or proguard.");
        };
        NameMappings mappings = project.renames().nameMappings(jar);
        File file = new File(arguments.string("path"));
        Files.writeString(file.toPath(), format.write(mappings), StandardCharsets.UTF_8);
        return "Wrote " + mappings.classes().size() + " renamed classes of " + jar + " as " + format.displayName()
            + " to " + file.getAbsolutePath();
    }

    private String exportJar(Arguments arguments) throws Exception {
        String jar = jar(arguments, "jar");
        File destination = new File(arguments.string("path"));
        long changeCount = project.renames().changeCount();
        JarExportService.ExportResult result = project.jars().write(project.jars().snapshot(jar), destination);
        project.renames().markExported(jar, changeCount);
        return describeExport(result, destination);
    }

    private String overwriteOriginalJar(Arguments arguments) throws Exception {
        String jar = jar(arguments, "jar");
        JarOperations.JarSnapshot snapshot = project.jars().snapshot(jar);
        if (snapshot.source() == null) {
            throw new ToolException(jar + " was not loaded from a JAR file.");
        }
        long changeCount = project.renames().changeCount();
        List<RenameStep> written = project.renames().history().stream()
            .filter(step -> step.jarName().equals(jar))
            .toList();
        JarExportService.ExportResult result = project.jars().overwriteOriginal(snapshot);
        project.renames().commitOverwrittenJar(jar, written, changeCount);
        return describeExport(result, snapshot.source())
            + "\nThe previous file is " + snapshot.source().getName() + ".bak.";
    }

    private String matchClasses(Arguments arguments) throws ToolException {
        String sourceJar = jar(arguments, "source_jar");
        String targetJar = jar(arguments, "target_jar");
        ClassNode source = classNode(sourceJar, arguments.className("class"));
        List<ClassMatch> matches = project.mappings().findCandidates(targetJar, source,
            arguments.integer("min_similarity", 50), Math.max(1, arguments.integer("limit", 20)));
        if (matches.isEmpty()) {
            return "No class in " + targetJar + " is similar enough to " + source.name + ".";
        }
        Optional<ClassMappingDecision> accepted = project.mappings().accepted(sourceJar, targetJar, source);
        StringBuilder out = new StringBuilder("Candidates for " + source.name + " in " + targetJar + ":\n");
        for (ClassMatch match : matches) {
            out.append("  ").append(match.similarityPercent()).append("%  ").append(match.classNode().name);
            if (accepted.isPresent() && accepted.get().targetClassName().equals(match.classNode().name)) {
                out.append("  [accepted]");
            } else if (project.mappings().isRejected(sourceJar, targetJar, source, match.classNode())) {
                out.append("  [rejected]");
            }
            out.append('\n');
        }
        return out.toString().strip();
    }

    private String acceptClassMapping(Arguments arguments) throws ToolException {
        String sourceJar = jar(arguments, "source_jar");
        String targetJar = jar(arguments, "target_jar");
        ClassNode source = classNode(sourceJar, arguments.className("class"));
        ClassNode target = classNode(targetJar, arguments.className("target_class"));
        ClassMatch match = project.mappings().findCandidates(targetJar, source, 0, Integer.MAX_VALUE).stream()
            .filter(candidate -> candidate.classNode() == target)
            .findFirst()
            .orElse(new ClassMatch(target, 0));
        try {
            project.mappings().accept(sourceJar, targetJar, source, match);
        } catch (IllegalArgumentException exception) {
            throw new ToolException(exception.getMessage());
        }
        return "Accepted " + sourceJar + " " + source.name + " -> " + targetJar + " " + target.name
            + " (" + match.similarityPercent() + "% similar).";
    }

    private String autoMatch(Arguments arguments) throws ToolException {
        String sourceJar = jar(arguments, "source_jar");
        String targetJar = jar(arguments, "target_jar");
        int minimum = arguments.integer("min_similarity", 90);
        if (minimum < 0 || minimum > 100) {
            throw new ToolException("min_similarity must be between 0 and 100.");
        }
        List<ClassMappingDecision> added = project.mappings().autoAcceptUnique(sourceJar, targetJar, minimum);
        String result = "Accepted " + added.size() + " unique matches of at least " + minimum + "%.";
        if (!arguments.bool("use_members", true)) {
            return result;
        }
        List<ClassVoting.VotedMatch> voted;
        try {
            voted = project.mappings().voteClassesLater(sourceJar, targetJar).call();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        int fromMembers = project.mappings().acceptVoted(sourceJar, targetJar, voted).size();
        return result + " Accepted " + fromMembers + " more from their members.";
    }

    private String matchMembers(Arguments arguments) throws ToolException {
        String sourceJar = jar(arguments, "source_jar");
        String targetJar = jar(arguments, "target_jar");
        int limit = arguments.integer("limit", 200);
        if (project.mappings().accepted(sourceJar, targetJar).isEmpty()) {
            throw new ToolException("No classes of " + sourceJar + " are mapped to " + targetJar
                + ". Accept class mappings first, with auto_match_classes or accept_class_mapping.");
        }
        MemberMatches matches = project.mappings().matchMembers(sourceJar, targetJar);
        List<String> lines = new ArrayList<>();
        for (MemberMatch match : matches.methods()) {
            lines.add(describe("method", match));
        }
        for (MemberMatch match : matches.fields()) {
            lines.add(describe("field", match));
        }
        return String.format("Matched %d of %d methods and %d of %d fields of %s to %s:%n", matches.methods().size(),
            matches.sourceMethods(), matches.fields().size(), matches.sourceFields(), sourceJar, targetJar)
            + String.join("\n", limitList(lines, limit));
    }

    private static String describe(String kind, MemberMatch match) {
        return "  " + kind + " " + match.source() + " -> " + match.target() + "  (" + match.confidencePercent() + "%, "
            + match.reason().description() + ")";
    }

    private String carryNames(Arguments arguments) throws ToolException {
        String sourceJar = jar(arguments, "source_jar");
        String targetJar = jar(arguments, "target_jar");
        List<ClassMappingDecision> classMappings = project.mappings().accepted(sourceJar, targetJar);
        if (classMappings.isEmpty()) {
            throw new ToolException("No classes of " + sourceJar + " are mapped to " + targetJar
                + ". Accept class mappings first, with auto_match_classes or accept_class_mapping.");
        }
        MemberMatches matches = project.mappings().matchMembers(sourceJar, targetJar);
        RenameOperations.NameTransfer transfer = project.renames().planNameTransfer(sourceJar, targetJar,
            classMappings, matches, arguments.bool("include_loaded_names", false));
        RenameBatch batch = transfer.batch();
        String counts = String.format("%d classes, %d fields and %d methods (including overrides) of %s",
            batch.classes().size(), batch.fields().size(), batch.methods().size(), targetJar);
        StringBuilder notes = new StringBuilder();
        if (transfer.alreadyNamed() > 0) {
            notes.append("\n").append(transfer.alreadyNamed()).append(" matches were renamed already and keep their names.");
        }
        if (!transfer.leftOut().isEmpty()) {
            notes.append("\n").append(transfer.leftOut().size()).append(" left out:\n")
                .append(String.join("\n", limitList(transfer.leftOut(), 30)));
        }
        if (arguments.bool("dry_run", false) || batch.size() == 0) {
            return (batch.size() == 0 ? "Nothing to rename in " + targetJar + "." : "Would rename " + counts + ".")
                + notes;
        }
        RenameResult result = project.renames().applyBatch(batch);
        return "Renamed " + counts + ", changing " + result.referenceCount() + " references in " + result.fileCount()
            + " classes. undo reverts all of it." + notes;
    }

    private String listClassMappings() {
        List<ClassMappingDecision> mappings = project.mappings().allAccepted();
        if (mappings.isEmpty()) {
            return "No class mappings accepted.";
        }
        StringBuilder out = new StringBuilder(mappings.size() + " accepted class mappings:\n");
        for (ClassMappingDecision mapping : mappings) {
            out.append("  ").append(mapping.sourceJar()).append(' ').append(mapping.sourceClassName()).append(" -> ")
                .append(mapping.targetJar()).append(' ').append(mapping.targetClassName()).append("  (")
                .append(mapping.similarityPercent()).append("%)\n");
        }
        return out.toString().strip();
    }

    private String openProject(Arguments arguments) throws Exception {
        File file = new File(arguments.string("path"));
        ProjectSession session = sessions.load(file);
        Project opened = Project.open(logger, session);
        project.close();
        project = opened;
        configureDecompilerCache();
        projectFile = file.getAbsoluteFile();
        layout = session.layout();
        project.warmUpDecompiler(project.jars().classesByJar().keySet());
        return "Opened " + projectFile + " with " + session.renames().size() + " rename steps, "
            + session.mappings().size() + " class mappings and " + session.bookmarks().size() + " bookmarks.\n\n"
            + listJars();
    }

    private String saveProject(Arguments arguments) throws Exception {
        String path = arguments.optionalString("path");
        File file = path == null ? projectFile : new File(path);
        if (file == null) {
            throw new ToolException("Give a path: this project has not been saved or opened from a file.");
        }
        sessions.save(file, project.toSession(layout));
        projectFile = file.getAbsoluteFile();
        return "Saved the project to " + projectFile + ".";
    }

    private String jar(Arguments arguments, String name) throws ToolException {
        String jar = arguments.string(name);
        if (project.jars().classes(jar) == null) {
            List<String> loaded = project.jars().classesByJar().keySet().stream().sorted().toList();
            throw new ToolException("No JAR named " + jar + " is loaded. Loaded: "
                + (loaded.isEmpty() ? "none" : String.join(", ", loaded)));
        }
        return jar;
    }

    private ClassNode classNode(String jar, String className) throws ToolException {
        return project.jars().findClass(new ClassRef(jar, className))
            .orElseThrow(() -> new ToolException("No class " + className + " in " + jar
                + ". Use list_classes or search to find it."));
    }

    /**
     * The class itself, or the field or method the arguments name. A member may be inherited when its descriptor
     * is given.
     */
    private Declaration member(Arguments arguments, ClassNode classNode, String jar) throws ToolException {
        String field = arguments.optionalString("field");
        String method = arguments.optionalString("method");
        String descriptor = arguments.optionalString("descriptor");
        if (field != null && method != null) {
            throw new ToolException("Give field or method, not both.");
        }
        if (field == null && method == null) {
            return new Declaration.ClassDeclaration(classNode);
        }
        boolean isField = field != null;
        String name = isField ? field : method;
        List<Declaration> declared = new ArrayList<>();
        if (isField) {
            classNode.fields.stream()
                .filter(candidate -> candidate.name.equals(name))
                .filter(candidate -> descriptor == null || candidate.desc.equals(descriptor))
                .forEach(candidate -> declared.add(new Declaration.FieldDeclaration(classNode, candidate)));
        } else {
            classNode.methods.stream()
                .filter(candidate -> candidate.name.equals(name))
                .filter(candidate -> descriptor == null || candidate.desc.equals(descriptor))
                .forEach(candidate -> declared.add(new Declaration.MethodDeclaration(classNode, candidate)));
        }
        if (declared.size() == 1) {
            return declared.getFirst();
        }
        String kind = isField ? "field" : "method";
        String members = isField ? memberList(classNode.fields.stream().map(f -> f.name + " " + f.desc).toList())
            : memberList(classNode.methods);
        if (declared.size() > 1) {
            throw new ToolException(classNode.name + " declares more than one " + kind + " named " + name
                + "; give its descriptor. Candidates: " + members);
        }
        if (descriptor != null) {
            Symbol symbol = isField ? new Symbol.FieldSymbol(classNode.name, name, descriptor)
                : new Symbol.MethodSymbol(classNode.name, name, descriptor);
            Optional<Declaration> inherited = project.jars().resolve(jar, symbol);
            if (inherited.isPresent()) {
                return inherited.get();
            }
        }
        throw new ToolException(classNode.name + " declares no " + kind + " " + name
            + (descriptor == null ? "" : descriptor) + ". Its " + kind + "s: " + members);
    }

    private static void appendReferences(StringBuilder out, String title, List<ReferenceResult> references, int limit) {
        out.append(title).append(" (").append(references.size()).append(")");
        if (references.size() > limit) {
            out.append(", showing ").append(limit);
        }
        out.append(":\n");
        for (ReferenceResult reference : references.subList(0, Math.min(limit, references.size()))) {
            out.append("  ").append(reference.kind().name().toLowerCase(Locale.ROOT)).append(": ")
                .append(reference.text()).append('\n');
        }
    }

    private static String describe(RenameStep step) {
        return switch (step) {
            case RenameOperation operation -> operation.type().name().toLowerCase(Locale.ROOT) + " rename in "
                + operation.jarName() + ": " + (operation.type().name().equals("CLASS") ? ""
                : operation.ownerName() + ".") + operation.oldName()
                + (operation.descriptor() == null ? "" : " " + operation.descriptor()) + " -> " + operation.newName();
            case RenameBatch batch -> "import of " + batch.source() + " into " + batch.jarName() + " ("
                + batch.classes().size() + " classes, " + batch.fields().size() + " fields, "
                + batch.methods().size() + " methods)";
        };
    }

    private static String describeExport(JarExportService.ExportResult result, File destination) {
        String text = "Wrote " + result.classes() + " classes and " + result.resources() + " resources to "
            + destination.getAbsolutePath() + ".";
        if (result.removedSignatureFiles() > 0) {
            text += " Left out " + result.removedSignatureFiles() + " signature files that no longer match.";
        }
        return text;
    }

    private static String kind(ClassNode classNode) {
        if ((classNode.access & Opcodes.ACC_ANNOTATION) != 0) {
            return "@interface";
        }
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return "interface";
        }
        if ((classNode.access & Opcodes.ACC_ENUM) != 0) {
            return "enum";
        }
        return "class";
    }

    private static String modifiers(int access) {
        String text = Modifier.toString(access & (Modifier.fieldModifiers() | Modifier.methodModifiers()));
        return text.isEmpty() ? "" : text + " ";
    }

    private static String memberList(List<?> members) {
        List<String> names = members.stream()
            .map(member -> member instanceof MethodNode method ? method.name + method.desc : String.valueOf(member))
            .toList();
        return names.isEmpty() ? "none" : String.join(", ", limitList(names, 40));
    }

    private static List<String> limitList(List<String> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        List<String> limited = new ArrayList<>(values.subList(0, limit));
        limited.add("... " + (values.size() - limit) + " more");
        return limited;
    }

    private static String truncate(String text, Arguments arguments) throws ToolException {
        int maxChars = Math.max(1000, arguments.integer("max_chars", DEFAULT_MAX_CHARS));
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n... truncated: " + (text.length() - maxChars)
            + " more characters. Raise max_chars, or ask get_bytecode for one method.";
    }
}
