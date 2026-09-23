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

/**
 * Something the server can hand over by URI rather than by tool call.
 * <p>
 * The distinction MCP draws is between what a model decides to do and what a person attaches. A
 * flamegraph export is both: a model asks for one while reasoning, and a reader wants to pin one into
 * the conversation and talk about it. As a tool it costs a call and a round trip; as a resource the
 * client can fetch it directly and show it in its own UI.
 *
 * @param uri         what identifies it, e.g. {@code jeffrey://profiles}
 * @param name        a short name for a list
 * @param description what it holds
 * @param mimeType    how to render it
 */
public record McpResource(String uri, String name, String description, String mimeType) {

    public static final String TEXT_MARKDOWN = "text/markdown";
    public static final String APPLICATION_JSON = "application/json";
}
