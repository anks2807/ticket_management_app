package com.ttn.tickets.web.dto;

import java.util.List;
import java.util.Set;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@JsonDeserialize(using = EditTicketRequest.Deserializer.class)
public final class EditTicketRequest {

    static final Set<String> FIELDS = Set.of(
            "title", "description", "priority", "category", "assignee", "resolutionNotes");

    private final JsonNode body;

    private EditTicketRequest(JsonNode body) {
        this.body = body;
    }

    public boolean isEmpty() {
        return body.isEmpty();
    }

    public boolean has(String field) {
        return body.has(field);
    }

    public boolean isNull(String field) {
        JsonNode value = body.get(field);
        return value != null && value.isNull();
    }

    public boolean isTextual(String field) {
        JsonNode value = body.get(field);
        return value != null && value.isTextual();
    }

    public String text(String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.stringValue();
    }

    public static final class Deserializer extends ValueDeserializer<EditTicketRequest> {

        @Override
        public EditTicketRequest deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = context.readTree(parser);
            if (node == null || !node.isObject()) {
                throw DatabindException.from(parser, "Edit body must be a JSON object");
            }
            for (String name : node.propertyNames()) {
                if (!FIELDS.contains(name)) {
                    throw UnrecognizedPropertyException.from(
                            parser, EditTicketRequest.class, name, List.copyOf(FIELDS));
                }
            }
            return new EditTicketRequest(node);
        }
    }
}
