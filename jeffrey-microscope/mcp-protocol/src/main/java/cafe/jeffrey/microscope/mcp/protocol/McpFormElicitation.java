/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * An {@code elicitation/create} in form mode: a message and a flat form the host shows the user.
 * <p>
 * The form is a {@link McpFormSchema} rather than any JSON object, so a schema the specification does
 * not allow — nested, or of a type a host cannot render as a field — cannot be asked for at all.
 *
 * @param message         what the user is asked, in a sentence that stands on its own
 * @param requestedSchema the form
 */
public record McpFormElicitation(String message, McpFormSchema requestedSchema) implements McpInputRequest {

    private static final String METHOD_ELICITATION_CREATE = "elicitation/create";
    private static final String MODE_FORM = "form";
    private static final String FIELD_METHOD = "method";
    private static final String FIELD_PARAMS = "params";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_MESSAGE = "message";
    private static final String FIELD_REQUESTED_SCHEMA = "requestedSchema";

    public McpFormElicitation {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("An elicitation needs a message for the user");
        }
        Objects.requireNonNull(requestedSchema, "requestedSchema");
    }

    @Override
    public ObjectNode toJson() {
        ObjectNode request = McpJson.createObject();
        request.put(FIELD_METHOD, METHOD_ELICITATION_CREATE);
        ObjectNode params = request.putObject(FIELD_PARAMS);
        params.put(FIELD_MODE, MODE_FORM);
        params.put(FIELD_MESSAGE, message);
        params.set(FIELD_REQUESTED_SCHEMA, requestedSchema.toJson());
        return request;
    }

    @Override
    public boolean answerableBy(McpClientCapabilities client) {
        return client.elicitationForm();
    }
}
