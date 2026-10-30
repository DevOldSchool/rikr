package org.de.rikr.agent;

/**
 * A tool call that cannot be done as asked. The message goes back to the agent as the tool's error result.
 */
class ToolException extends Exception {
    ToolException(String message) {
        super(message);
    }
}
