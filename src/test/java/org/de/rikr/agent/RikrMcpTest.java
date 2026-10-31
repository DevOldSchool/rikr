package org.de.rikr.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.de.rikr.jar.JarExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RikrMcpTest {
    @TempDir
    Path tempDir;

    private RikrTools tools;
    private McpServer server;
    private int nextId;

    @BeforeEach
    void start() {
        tools = new RikrTools(message -> {
        });
        PrintStream log = new PrintStream(new ByteArrayOutputStream());
        server = new McpServer("rikr", "test", RikrMcp.INSTRUCTIONS, tools.tools(), log);
    }

    @AfterEach
    void stop() {
        tools.close();
    }

    @Test
    void negotiatesTheProtocolAndListsTools() {
        JsonObject initialize = request("initialize", params("protocolVersion", "2025-03-26"));
        JsonObject tools = request("tools/list", new JsonObject());

        assertEquals("2025-03-26", initialize.getAsJsonObject("result").get("protocolVersion").getAsString());
        JsonObject serverInfo = initialize.getAsJsonObject("result").getAsJsonObject("serverInfo");
        assertEquals("rikr", serverInfo.get("name").getAsString());
        JsonArray list = tools.getAsJsonObject("result").getAsJsonArray("tools");
        assertTrue(list.size() > 15);
        for (var entry : list) {
            JsonObject tool = entry.getAsJsonObject();
            assertEquals("object", tool.getAsJsonObject("inputSchema").get("type").getAsString(), tool.toString());
        }
    }

    @Test
    void handshakeClientsGetTheirRevisionOrTheLatestHandshakeRevision() {
        JsonObject current = request("initialize", params("protocolVersion", "2025-11-25"));
        JsonObject unknown = request("initialize", params("protocolVersion", "2099-01-01"));
        JsonObject list = request("tools/list", new JsonObject());

        assertEquals("2025-11-25", current.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertEquals("2025-11-25", unknown.getAsJsonObject("result").get("protocolVersion").getAsString());
        assertFalse(list.getAsJsonObject("result").has("resultType"), "handshake results keep their old shape");
    }

    @Test
    void statelessClientsDiscoverListAndCallWithoutAHandshake() {
        JsonObject discover = modern("server/discover", new JsonObject(), "2026-07-28").getAsJsonObject("result");
        JsonObject list = modern("tools/list", new JsonObject(), "2026-07-28").getAsJsonObject("result");
        JsonObject callParams = params("name", "list_jars");
        callParams.add("arguments", new JsonObject());
        JsonObject call = modern("tools/call", callParams, "2026-07-28").getAsJsonObject("result");

        assertEquals("[\"2026-07-28\"]", discover.getAsJsonArray("supportedVersions").toString());
        assertTrue(discover.getAsJsonObject("capabilities").has("tools"));
        assertEquals(RikrMcp.INSTRUCTIONS, discover.get("instructions").getAsString());
        for (JsonObject result : List.of(discover, list, call)) {
            assertEquals("complete", result.get("resultType").getAsString(), result.toString());
            assertEquals("rikr", result.getAsJsonObject("_meta")
                .getAsJsonObject("io.modelcontextprotocol/serverInfo").get("name").getAsString());
        }
        for (JsonObject result : List.of(discover, list)) {
            assertEquals(3_600_000, result.get("ttlMs").getAsLong());
            assertEquals("public", result.get("cacheScope").getAsString());
        }
        assertTrue(list.getAsJsonArray("tools").size() > 15);
        assertEquals("No JARs are loaded.", text(call));
    }

    @Test
    void statelessRequestsAreCheckedOneByOne() {
        JsonObject unsupported = modern("tools/list", new JsonObject(), "2030-01-01");
        JsonObject data = unsupported.getAsJsonObject("error").getAsJsonObject("data");
        JsonObject noCapabilities = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("io.modelcontextprotocol/protocolVersion", "2026-07-28");
        noCapabilities.add("_meta", meta);
        JsonObject noVersion = new JsonObject();
        JsonObject versionlessMeta = new JsonObject();
        versionlessMeta.add("io.modelcontextprotocol/clientCapabilities", new JsonObject());
        noVersion.add("_meta", versionlessMeta);
        JsonObject withState = params("name", "list_jars");
        withState.addProperty("requestState", "forged");

        assertEquals(-32022, errorCode(unsupported));
        assertEquals("2030-01-01", data.get("requested").getAsString());
        assertEquals("2026-07-28", data.getAsJsonArray("supported").get(0).getAsString());
        assertEquals(-32602, errorCode(request("tools/list", noCapabilities)));
        assertEquals(-32602, errorCode(request("tools/list", noVersion)));
        assertEquals(-32601, errorCode(modern("ping", new JsonObject(), "2026-07-28")));
        assertEquals(-32601, errorCode(modern("initialize", new JsonObject(), "2026-07-28")));
        assertEquals(-32602, errorCode(modern("tools/call", withState, "2026-07-28")));
    }

    @Test
    void answersProtocolErrorsAndIgnoresNotifications() {
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        assertEquals(-32700, server.handle("{not json").getAsJsonObject("error").get("code").getAsInt());
        assertEquals(-32601, errorCode(request("resources/list", new JsonObject())));
        assertEquals(-32602, errorCode(request("tools/call", params("name", "nope"))));
    }

    @Test
    void servesMessagesLineByLine() throws Exception {
        String input = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n\n"
            + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
            + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        server.serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);

        List<String> lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size());
        assertEquals(2, JsonParser.parseString(lines.get(1)).getAsJsonObject().get("id").getAsInt());
    }

    @Test
    void toolErrorsComeBackAsErrorResultsTheAgentCanRead() {
        JsonObject result = call("describe_class", args("jar", "missing.jar", "class", "a"));

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("No JAR named missing.jar is loaded. Loaded: none", text(result));
    }

    @Test
    void loadsReadsRenamesUndoesAndSavesAProject() throws Exception {
        Path jar = writeJar();

        assertTrue(text(call("load_jars", paths(jar))).contains("game.jar: 2 classes"));
        assertTrue(text(call("list_classes", args("jar", "game.jar"))).contains("b  (0 fields, 2 methods)"));
        assertTrue(text(call("describe_class", args("jar", "game.jar", "class", "a"))).contains("private hp I"));
        assertTrue(text(call("get_bytecode", args("jar", "game.jar", "class", "b", "method", "read")))
            .contains("GETFIELD a.hp : I"));
        assertTrue(text(call("find_references", args("jar", "game.jar", "class", "a", "field", "hp")))
            .contains("Reads (1)"));
        assertEquals("No entry points found in game.jar.", text(call("list_entry_points", args("jar", "game.jar"))));
        String calls = text(call("get_call_graph", args("jar", "game.jar", "class", "b", "method", "make")));
        assertTrue(calls.contains("-> static b.read(La;)I"), calls);
        String flow = text(call("get_control_flow", args("jar", "game.jar", "class", "b", "method", "read")));
        assertTrue(flow.startsWith("b.read(La;)I: 1 blocks, 0 edges") && flow.contains("exits the method"), flow);

        JsonObject dryRun = call("rename",
            args("jar", "game.jar", "class", "a", "new_name", "game.Player", "dry_run", true));
        assertTrue(text(dryRun).startsWith("Would rename a to game/Player"), text(dryRun));
        assertTrue(text(call("describe_class", args("jar", "game.jar", "class", "a"))).contains("class a"));

        call("rename", args("jar", "game.jar", "class", "a", "new_name", "game.Player"));
        call("rename", args("jar", "game.jar", "class", "game/Player", "field", "hp", "new_name", "health"));
        String described = text(call("describe_class", args("jar", "game.jar", "class", "game.Player")));
        assertTrue(described.contains("Loaded as: a") && described.contains("private health I"), described);
        assertTrue(text(call("rename_history", new JsonObject())).contains("2 steps"));

        Path mappings = tempDir.resolve("game.tiny");
        call("export_mappings", args("jar", "game.jar", "path", mappings.toString(), "format", "tiny"));
        assertTrue(Files.readString(mappings).contains("c\ta\tgame/Player"));

        assertTrue(text(call("undo", new JsonObject())).contains("field rename"));
        assertTrue(text(call("decompile", args("jar", "game.jar", "class", "b"))).contains("game.Player"));

        Path project = tempDir.resolve("game.rikr");
        call("save_project", args("path", project.toString()));
        call("undo", new JsonObject());
        JsonObject opened = call("open_project", args("path", project.toString()));
        assertFalse(opened.get("isError").getAsBoolean(), text(opened));
        assertTrue(text(call("list_classes", args("jar", "game.jar"))).contains("game/Player"));

        JsonObject imported = call("import_mappings", args("jar", "game.jar", "path", mappings.toString()));
        assertTrue(text(imported).startsWith("Renamed 0 classes, 1 fields"), text(imported));
    }

    @Test
    void autoMatchesClassesByShapeAndThenFromTheirMembers() throws Exception {
        call("load_jars", paths(writeJar("old.jar", "a", "b"), writeJar("new.jar", "x", "y")));

        String matched = text(call("auto_match_classes", args("source_jar", "old.jar", "target_jar", "new.jar")));
        String byShapeOnly = text(call("auto_match_classes",
            args("source_jar", "old.jar", "target_jar", "new.jar", "use_members", false)));

        assertTrue(matched.matches("Accepted \\d+ unique matches of at least 90%\\. Accepted \\d+ more from their members\\."),
            matched);
        assertTrue(byShapeOnly.matches("Accepted \\d+ unique matches of at least 90%\\."), byShapeOnly);
        assertTrue(text(call("list_class_mappings", new JsonObject())).contains("old.jar a -> new.jar x"));
    }

    @Test
    void matchesMembersAndCarriesNamesToAnotherVersion() throws Exception {
        Path oldJar = writeJar("old.jar", "a", "b");
        Path newJar = writeJar("new.jar", "x", "y");
        call("load_jars", paths(oldJar, newJar));
        JsonObject unmapped = call("carry_names", args("source_jar", "old.jar", "target_jar", "new.jar"));
        assertTrue(unmapped.get("isError").getAsBoolean());
        assertTrue(text(unmapped).contains("Accept class mappings first"), text(unmapped));

        call("rename", args("jar", "old.jar", "class", "a", "new_name", "game.Player"));
        call("rename", args("jar", "old.jar", "class", "game/Player", "field", "hp", "new_name", "health"));
        call("rename", args("jar", "old.jar", "class", "b", "method", "read", "new_name", "readHealth"));
        call("accept_class_mapping", args("source_jar", "old.jar", "target_jar", "new.jar",
            "class", "game/Player", "target_class", "x"));
        call("accept_class_mapping", args("source_jar", "old.jar", "target_jar", "new.jar",
            "class", "b", "target_class", "y"));

        String matched = text(call("match_members", args("source_jar", "old.jar", "target_jar", "new.jar")));
        assertTrue(matched.startsWith("Matched 2 of 2 methods and 1 of 1 fields"), matched);
        assertTrue(matched.contains("field game/Player.health:I -> x.hp:I"), matched);

        String loadedToo = text(call("carry_names", args("source_jar", "old.jar", "target_jar", "new.jar",
            "include_loaded_names", true, "dry_run", true)));
        // b, read's owner, kept its loaded name, but one-letter class names are what this JAR's obfuscator gives,
        // so b is not carried even when loaded names are.
        assertTrue(loadedToo.startsWith("Would rename 1 classes, 1 fields and 1 methods"), loadedToo);
        String dryRun = text(call("carry_names",
            args("source_jar", "old.jar", "target_jar", "new.jar", "dry_run", true)));
        assertTrue(dryRun.startsWith("Would rename 1 classes, 1 fields and 1 methods"), dryRun);
        String carried = text(call("carry_names", args("source_jar", "old.jar", "target_jar", "new.jar")));
        assertTrue(carried.startsWith("Renamed 1 classes, 1 fields and 1 methods"), carried);
        String described = text(call("describe_class", args("jar", "new.jar", "class", "y")));
        assertTrue(described.contains("readHealth(Lgame/Player;)I"), described);
        assertTrue(text(call("describe_class", args("jar", "new.jar", "class", "game/Player")))
            .contains("private health I"));
    }

    private Path writeJar() throws Exception {
        return writeJar("game.jar", "a", "b");
    }

    private Path writeJar(String fileName, String player, String reader) throws Exception {
        ClassNode a = classNode(player);
        a.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "hp", "I", null, null));
        ClassNode b = classNode(reader);
        MethodNode read = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "read", "(L" + player + ";)I",
            null, null);
        read.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        read.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, player, "hp", "I"));
        read.instructions.add(new InsnNode(Opcodes.IRETURN));
        read.maxStack = 1;
        read.maxLocals = 1;
        b.methods.add(read);
        MethodNode make = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "make", "()V", null, null);
        make.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        make.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, reader, "read", "(L" + player + ";)I", false));
        make.instructions.add(new InsnNode(Opcodes.POP));
        make.instructions.add(new InsnNode(Opcodes.RETURN));
        make.maxStack = 1;
        b.methods.add(make);
        Path jar = tempDir.resolve(fileName);
        JarExportService exporter = new JarExportService();
        exporter.writeJar(null, jar.toFile(), exporter.toEntries(List.of(a, b)));
        return jar;
    }

    private static ClassNode classNode(String name) {
        ClassNode classNode = new ClassNode();
        classNode.version = Opcodes.V1_8;
        classNode.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        classNode.name = name;
        classNode.superName = "java/lang/Object";
        return classNode;
    }

    private JsonObject call(String tool, JsonObject arguments) {
        JsonObject params = params("name", tool);
        params.add("arguments", arguments);
        JsonObject response = request("tools/call", params);
        return response.getAsJsonObject("result");
    }

    private JsonObject request(String method, JsonObject params) {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", ++nextId);
        request.addProperty("method", method);
        request.add("params", params);
        JsonObject response = server.handle(request.toString());
        assertEquals(nextId, response.get("id").getAsInt());
        return response;
    }

    /**
     * A 2026-07-28 request, which carries its protocol version and client capabilities in {@code _meta}.
     */
    private JsonObject modern(String method, JsonObject params, String version) {
        JsonObject meta = new JsonObject();
        meta.addProperty("io.modelcontextprotocol/protocolVersion", version);
        meta.add("io.modelcontextprotocol/clientCapabilities", new JsonObject());
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", "test");
        clientInfo.addProperty("version", "1");
        meta.add("io.modelcontextprotocol/clientInfo", clientInfo);
        params.add("_meta", meta);
        return request(method, params);
    }

    private static int errorCode(JsonObject response) {
        return response.getAsJsonObject("error").get("code").getAsInt();
    }

    private static String text(JsonObject result) {
        return result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    private static JsonObject params(String key, String value) {
        JsonObject params = new JsonObject();
        params.addProperty(key, value);
        return params;
    }

    private static JsonObject paths(Path... paths) {
        JsonObject arguments = new JsonObject();
        JsonArray list = new JsonArray();
        for (Path path : paths) {
            list.add(path.toString());
        }
        arguments.add("paths", list);
        return arguments;
    }

    private static JsonObject args(Object... keysAndValues) {
        JsonObject arguments = new JsonObject();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            Object value = keysAndValues[index + 1];
            if (value instanceof Boolean flag) {
                arguments.addProperty((String) keysAndValues[index], flag);
            } else {
                arguments.addProperty((String) keysAndValues[index], (String) value);
            }
        }
        return arguments;
    }
}
