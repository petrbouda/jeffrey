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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a client declared it can do, read from {@code _meta["io.modelcontextprotocol/clientCapabilities"]}.
 * <p>
 * Only the two things the server acts on are read out: whether the client renders a form elicitation,
 * and which extensions it speaks. The whole declaration is kept as {@code raw} for diagnostics.
 *
 * @param elicitationForm whether the client accepts {@code elicitation/create} in form mode
 * @param extensions      the extension ids under {@code extensions}
 * @param raw             the declaration as the client sent it
 */
public record McpClientCapabilities(boolean elicitationForm, Set<String> extensions, ObjectNode raw) {

    public static final String TASKS_EXTENSION = "io.modelcontextprotocol/tasks";
    public static final String SKILLS_EXTENSION = "io.modelcontextprotocol/skills";

    /** A client that declared nothing. */
    public static final McpClientCapabilities NONE = new McpClientCapabilities(false, Set.of(), McpJson.createObject());

    private static final String FIELD_ELICITATION = "elicitation";
    private static final String FIELD_FORM = "form";
    private static final String FIELD_URL = "url";
    private static final String FIELD_EXTENSIONS = "extensions";
    private static final String NOT_AN_OBJECT = "The client capabilities in _meta must be an object";

    public McpClientCapabilities {
        extensions = Set.copyOf(extensions);
        raw = Objects.requireNonNull(raw, "raw").deepCopy();
    }

    /**
     * @throws McpProtocolException {@code -32602} (as {@link McpErrorCode#INVALID_META}) when the
     *                              declaration is absent or not an object
     */
    public static McpClientCapabilities parse(JsonNode declared) {
        if (declared == null || !declared.isObject()) {
            throw new McpProtocolException(McpErrorCode.INVALID_META, NOT_AN_OBJECT);
        }
        return new McpClientCapabilities(
                declaresFormElicitation(declared.get(FIELD_ELICITATION)),
                extensionIds(declared.get(FIELD_EXTENSIONS)),
                (ObjectNode) declared);
    }

    /**
     * Form mode is declared by {@code form}, or by an empty {@code elicitation} object — the shape from
     * before the modes existed, which meant form. {@code url} alone is not form.
     */
    private static boolean declaresFormElicitation(JsonNode elicitation) {
        if (elicitation == null || !elicitation.isObject()) {
            return false;
        }
        if (elicitation.has(FIELD_FORM)) {
            return true;
        }
        return !elicitation.has(FIELD_URL);
    }

    private static Set<String> extensionIds(JsonNode extensions) {
        Set<String> ids = new LinkedHashSet<>();
        if (extensions != null && extensions.isObject()) {
            ids.addAll(extensions.propertyNames());
        }
        return ids;
    }

    @Override
    public ObjectNode raw() {
        return raw.deepCopy();
    }
}
