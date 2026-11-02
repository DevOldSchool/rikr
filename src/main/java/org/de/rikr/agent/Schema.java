package org.de.rikr.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the JSON schema of a tool's arguments.
 */
final class Schema {
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    static Schema object() {
        return new Schema();
    }

    Schema string(String name, String description, boolean isRequired) {
        return property(name, "string", description, isRequired);
    }

    Schema integer(String name, String description) {
        return property(name, "integer", description, false);
    }

    Schema bool(String name, String description) {
        return property(name, "boolean", description, false);
    }

    Schema stringArray(String name, String description, boolean isRequired) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "array");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        property.add("items", items);
        property.addProperty("description", description);
        properties.add(name, property);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }

    Schema choice(String name, String description, boolean isRequired, String... values) {
        property(name, "string", description, isRequired);
        JsonArray choices = new JsonArray();
        for (String value : values) {
            choices.add(value);
        }
        properties.getAsJsonObject(name).add("enum", choices);
        return this;
    }

    JsonObject build() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        if (!required.isEmpty()) {
            schema.add("required", required);
        }
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private Schema property(String name, String type, String description, boolean isRequired) {
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        properties.add(name, property);
        if (isRequired) {
            required.add(name);
        }
        return this;
    }
}
