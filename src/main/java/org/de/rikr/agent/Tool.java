package org.de.rikr.agent;

import com.google.gson.JsonObject;

/**
 * A tool the MCP server offers: its name, what it does, the JSON schema of its arguments, and the code that
 * runs it.
 */
record Tool(String name, String description, JsonObject inputSchema, boolean readOnly, Handler handler) {

    @FunctionalInterface
    interface Handler {
        /**
         * @return the text sent back to the agent
         * @throws ToolException for a problem the agent can fix, such as an unknown class
         */
        String call(Arguments arguments) throws Exception;
    }
}
