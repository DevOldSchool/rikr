package org.de.rikr.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A tool call's arguments, with errors the agent can act on when one is missing or has the wrong type.
 */
final class Arguments {
    private final JsonObject values;

    Arguments(JsonObject values) {
        this.values = values == null ? new JsonObject() : values;
    }

    String string(String name) throws ToolException {
        String value = optionalString(name);
        if (value == null || value.isBlank()) {
            throw new ToolException("Missing argument: " + name);
        }
        return value;
    }

    String optionalString(String name) throws ToolException {
        JsonElement value = values.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ToolException("Argument " + name + " must be a string.");
        }
        return value.getAsString();
    }

    /**
     * A class name in internal form: {@code a.b.C} is accepted and read as {@code a/b/C}.
     */
    String className(String name) throws ToolException {
        return string(name).replace('.', '/');
    }

    int integer(String name, int defaultValue) throws ToolException {
        JsonElement value = values.get(name);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        try {
            return value.getAsInt();
        } catch (RuntimeException exception) {
            throw new ToolException("Argument " + name + " must be an integer.");
        }
    }

    boolean bool(String name, boolean defaultValue) throws ToolException {
        JsonElement value = values.get(name);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new ToolException("Argument " + name + " must be true or false.");
        }
        return value.getAsBoolean();
    }

    List<String> strings(String name) throws ToolException {
        JsonElement value = values.get(name);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty()) {
            throw new ToolException("Argument " + name + " must be a non-empty list of strings.");
        }
        List<String> strings = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            strings.add(element.getAsString());
        }
        return strings;
    }
}
