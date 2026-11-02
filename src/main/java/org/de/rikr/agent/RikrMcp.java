package org.de.rikr.agent;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Runs Rikr as a Model Context Protocol server on stdin and stdout, so agents such as Claude Code can load JARs,
 * read bytecode and decompiled Java, follow references, rename, and import or export mappings without the GUI.
 * <p>
 * stdout carries only protocol messages; everything else is written to stderr.
 */
public final class RikrMcp {
    static final String INSTRUCTIONS = """
        Rikr is a reverse engineering workbench for Java bytecode, mostly used on obfuscated JARs such as game \
        clients. This server holds one project in memory for the session.

        Typical flow: load_jars (or open_project), then list_classes / search to find classes, describe_class, \
        get_bytecode and decompile to read them, find_references to follow usage, and rename to give classes, \
        fields and methods meaningful names. Renames update every reference in the JAR and can be undone. \
        Use dry_run first for renames and mapping imports. Class names are internal names such as a/b/C.

        Nothing on disk changes until save_project, export_jar, overwrite_original_jar or export_mappings is \
        called. JARs are identified by their file name, as list_jars shows.""";

    private RikrMcp() {
    }

    public static void main(String[] args) throws IOException {
        PrintStream protocol = System.out;
        PrintStream diagnostics = System.err;
        // Libraries that print to System.out would corrupt the protocol stream, so send their output to stderr.
        System.setOut(diagnostics);
        System.setProperty("java.awt.headless", "true");

        try (RikrTools tools = new RikrTools(message -> diagnostics.println("[rikr] " + message))) {
            McpServer server = new McpServer("rikr", version(), INSTRUCTIONS, tools.tools(), diagnostics);
            server.serve(System.in, protocol);
        }
        // The decompiler's worker threads must not keep the process alive once the client disconnects.
        System.exit(0);
    }

    private static String version() {
        String version = RikrMcp.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
