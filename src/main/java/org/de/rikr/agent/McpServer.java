package org.de.rikr.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Model Context Protocol server over stdio: one JSON-RPC message per line on stdin, one per line on stdout.
 * Requests are handled one at a time, in order, so tools never run concurrently.
 * <p>
 * It serves both eras of the protocol, choosing per request as the specification's dual-era rules describe:
 * <ul>
 *   <li>A request whose {@code params._meta} carries {@code io.modelcontextprotocol/protocolVersion} is a
 *   stateless 2026-07-28 request: {@code server/discover}, {@code tools/list} and {@code tools/call}, with
 *   {@code resultType} on every result and caching hints on lists.</li>
 *   <li>Any other request follows the handshake revisions up to 2025-11-25: {@code initialize}, {@code ping},
 *   {@code tools/list} and {@code tools/call}.</li>
 * </ul>
 */
public final class McpServer {
    /** The stateless revision, where every request names its protocol version. */
    static final String MODERN_PROTOCOL = "2026-07-28";
    /** The newest revision that starts with an {@code initialize} handshake. */
    static final String LATEST_HANDSHAKE_PROTOCOL = "2025-11-25";
    private static final List<String> HANDSHAKE_PROTOCOLS =
        List.of(LATEST_HANDSHAKE_PROTOCOL, "2025-06-18", "2025-03-26", "2024-11-05");
    private static final List<String> SUPPORTED_PROTOCOLS = List.of(MODERN_PROTOCOL, LATEST_HANDSHAKE_PROTOCOL,
        "2025-06-18", "2025-03-26", "2024-11-05");

    private static final String META_PREFIX = "io.modelcontextprotocol/";
    private static final String META_PROTOCOL_VERSION = META_PREFIX + "protocolVersion";
    private static final String META_CLIENT_CAPABILITIES = META_PREFIX + "clientCapabilities";
    private static final String META_SERVER_INFO = META_PREFIX + "serverInfo";
    // The tools never change while the server runs, so clients may reuse a listing for an hour.
    private static final long LIST_TTL_MS = 3_600_000;

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final String name;
    private final String version;
    private final String instructions;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final PrintStream log;

    McpServer(String name, String version, String instructions, List<Tool> tools, PrintStream log) {
        this.name = name;
        this.version = version;
        this.instructions = instructions;
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
        this.log = log;
    }

    /**
     * Serve until stdin is closed.
     */
    void serve(InputStream input, OutputStream output) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        PrintStream writer = new PrintStream(output, true, StandardCharsets.UTF_8);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonObject response = handle(line);
            if (response != null) {
                writer.println(gson.toJson(response));
            }
        }
    }

    /**
     * The response to one message, or null for a notification.
     */
    JsonObject handle(String line) {
        JsonObject message;
        try {
            JsonElement parsed = JsonParser.parseString(line);
            if (!parsed.isJsonObject()) {
                return error(null, INVALID_REQUEST, "Expected a JSON-RPC object.");
            }
            message = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            return error(null, PARSE_ERROR, "Invalid JSON: " + exception.getMessage());
        }
        JsonElement id = message.get("id");
        JsonElement methodElement = message.get("method");
        if (methodElement == null || !methodElement.isJsonPrimitive()) {
            // A response to a request of ours; this server sends none.
            return id == null ? null : error(id, INVALID_REQUEST, "Missing method.");
        }
        String method = methodElement.getAsString();
        if (id == null) {
            // Notifications, such as notifications/initialized, need no answer.
            return null;
        }
        JsonObject params = message.has("params") && message.get("params").isJsonObject()
            ? message.getAsJsonObject("params")
            : new JsonObject();
        JsonObject meta = params.has("_meta") && params.get("_meta").isJsonObject()
            ? params.getAsJsonObject("_meta")
            : new JsonObject();
        boolean modern = meta.keySet().stream().anyMatch(key -> key.startsWith(META_PREFIX));
        return modern ? handleModern(id, method, params, meta) : handleHandshake(id, method, params);
    }

    /**
     * A request of the handshake revisions, up to 2025-11-25.
     */
    private JsonObject handleHandshake(JsonElement id, String method, JsonObject params) {
        return switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, new JsonObject());
            case "tools/list" -> result(id, listTools());
            case "tools/call" -> callTool(id, params, false);
            default -> error(id, METHOD_NOT_FOUND, "Unknown method: " + method);
        };
    }

    /**
     * A stateless 2026-07-28 request. Nothing from earlier requests is used: each one carries its own version
     * and client capabilities.
     */
    private JsonObject handleModern(JsonElement id, String method, JsonObject params, JsonObject meta) {
        JsonElement version = meta.get(META_PROTOCOL_VERSION);
        if (version == null || !version.isJsonPrimitive()) {
            return error(id, INVALID_PARAMS, "Missing _meta." + META_PROTOCOL_VERSION + ".");
        }
        if (!MODERN_PROTOCOL.equals(version.getAsString())) {
            JsonObject data = new JsonObject();
            JsonArray supported = new JsonArray();
            SUPPORTED_PROTOCOLS.forEach(supported::add);
            data.add("supported", supported);
            data.addProperty("requested", version.getAsString());
            return error(id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
        }
        if (!meta.has(META_CLIENT_CAPABILITIES) || !meta.get(META_CLIENT_CAPABILITIES).isJsonObject()) {
            return error(id, INVALID_PARAMS, "Missing _meta." + META_CLIENT_CAPABILITIES + ".");
        }
        JsonObject response = switch (method) {
            case "server/discover" -> result(id, discover());
            case "tools/list" -> result(id, cacheable(listTools()));
            case "tools/call" -> callTool(id, params, true);
            // initialize, ping and the other removed methods do not exist in this revision.
            default -> error(id, METHOD_NOT_FOUND, "Unknown method: " + method);
        };
        if (response.has("result")) {
            JsonObject result = response.getAsJsonObject("result");
            result.addProperty("resultType", "complete");
            JsonObject resultMeta = new JsonObject();
            resultMeta.add(META_SERVER_INFO, serverInfo());
            result.add("_meta", resultMeta);
        }
        return response;
    }

    private JsonObject discover() {
        JsonObject result = new JsonObject();
        JsonArray versions = new JsonArray();
        versions.add(MODERN_PROTOCOL);
        result.add("supportedVersions", versions);
        result.add("capabilities", capabilities());
        result.addProperty("instructions", instructions);
        return cacheable(result);
    }

    private static JsonObject cacheable(JsonObject result) {
        result.addProperty("ttlMs", LIST_TTL_MS);
        // Every client sees the same tools.
        result.addProperty("cacheScope", "public");
        return result;
    }

    private JsonObject capabilities() {
        JsonObject capabilities = new JsonObject();
        JsonObject toolCapability = new JsonObject();
        toolCapability.addProperty("listChanged", false);
        capabilities.add("tools", toolCapability);
        return capabilities;
    }

    private JsonObject serverInfo() {
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", name);
        serverInfo.addProperty("version", version);
        return serverInfo;
    }

    private JsonObject initialize(JsonObject params) {
        JsonElement requested = params.get("protocolVersion");
        String protocol = requested != null && requested.isJsonPrimitive()
            && HANDSHAKE_PROTOCOLS.contains(requested.getAsString())
            ? requested.getAsString()
            : LATEST_HANDSHAKE_PROTOCOL;
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", protocol);
        result.add("capabilities", capabilities());
        result.add("serverInfo", serverInfo());
        result.addProperty("instructions", instructions);
        return result;
    }

    private JsonObject listTools() {
        JsonArray list = new JsonArray();
        for (Tool tool : tools.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            JsonObject annotations = new JsonObject();
            annotations.addProperty("readOnlyHint", tool.readOnly());
            entry.add("annotations", annotations);
            list.add(entry);
        }
        JsonObject result = new JsonObject();
        result.add("tools", list);
        return result;
    }

    private JsonObject callTool(JsonElement id, JsonObject params, boolean modern) {
        JsonElement toolName = params.get("name");
        Tool tool = toolName == null ? null : tools.get(toolName.getAsString());
        if (tool == null) {
            return error(id, INVALID_PARAMS, "Unknown tool: " + (toolName == null ? "(none)" : toolName.getAsString()));
        }
        if (modern && params.has("requestState")) {
            // State is only valid when this server issued it, and its tools never ask the client for input.
            return error(id, INVALID_PARAMS, "This server never asks for input, so requestState is not valid.");
        }
        JsonElement arguments = params.get("arguments");
        String text;
        boolean failed = false;
        try {
            text = tool.handler().call(new Arguments(arguments != null && arguments.isJsonObject()
                ? arguments.getAsJsonObject()
                : null));
        } catch (ToolException exception) {
            text = exception.getMessage();
            failed = true;
        } catch (Exception | LinkageError exception) {
            StringWriter trace = new StringWriter();
            exception.printStackTrace(new PrintWriter(trace));
            log.println("Tool " + tool.name() + " failed:\n" + trace);
            text = tool.name() + " failed: " + exception;
            failed = true;
        }
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject result = new JsonObject();
        result.add("content", contents);
        result.addProperty("isError", failed);
        return result(id, result);
    }

    private static JsonObject result(JsonElement id, JsonObject result) {
        JsonObject response = envelope(id);
        response.add("result", result);
        return response;
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        return error(id, code, message, null);
    }

    private static JsonObject error(JsonElement id, int code, String message, JsonObject data) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (data != null) {
            error.add("data", data);
        }
        JsonObject response = envelope(id);
        response.add("error", error);
        return response;
    }

    private static JsonObject envelope(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        return response;
    }
}
