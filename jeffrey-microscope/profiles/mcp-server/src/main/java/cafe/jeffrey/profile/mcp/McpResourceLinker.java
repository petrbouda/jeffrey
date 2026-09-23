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
package cafe.jeffrey.profile.mcp;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Turns one tool call into the resource links that answer the same question.
 * <p>
 * This sits beside the toolset rather than inside it because the mapping is between a tool and a
 * <em>URI</em>, and the envelope is deliberately ignorant of what any server's URIs look like. The
 * endpoint that owns the resource templates owns this too, so the two cannot disagree about which
 * URIs exist.
 */
@FunctionalInterface
public interface McpResourceLinker {

    /** A linker for an endpoint whose tools have no resource counterpart. */
    McpResourceLinker NONE = (toolName, arguments) -> List.of();

    /**
     * @param toolName  the tool that just answered
     * @param arguments the arguments it was called with, possibly null
     * @return the links to attach, empty when the tool has no resource counterpart
     */
    List<McpResourceLink> linksFor(String toolName, JsonNode arguments);
}
