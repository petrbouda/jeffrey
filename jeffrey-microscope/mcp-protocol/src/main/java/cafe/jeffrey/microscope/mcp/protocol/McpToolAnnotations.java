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

/**
 * The behavioural hints a client shows a user before approving a tool call.
 * <p>
 * MCP calls these {@code annotations} on a tool. They exist so a reader can tell a tool that only looks
 * at something from one that creates, changes or removes something, without having to read every
 * description. {@code openWorld} marks a tool that reaches past the server — another machine, or another
 * process — whose answer is outside the server's control.
 *
 * @param readOnly   the tool observes and changes nothing
 * @param destructive the tool can destroy or overwrite something that existed before it ran
 * @param idempotent calling it twice with the same arguments has the same effect as calling it once
 * @param openWorld  the tool reaches a system beyond this installation
 */
public record McpToolAnnotations(
        boolean readOnly,
        boolean destructive,
        boolean idempotent,
        boolean openWorld) {

    /**
     * A tool that reads and reports what it found.
     */
    public static final McpToolAnnotations READ_ONLY = new McpToolAnnotations(true, false, true, false);

    /**
     * A tool that creates something. Not destructive: it adds rather than replaces, and returns what
     * already exists rather than building it twice, which is what makes it idempotent.
     */
    public static final McpToolAnnotations CREATES = new McpToolAnnotations(false, false, true, false);

    /**
     * A tool that reads, but reads from something outside this installation — another machine, or
     * another process on this one.
     * <p>
     * A group of tools is described by what most of it does; the one tool in it that writes says so
     * for itself, which is finer-grained than a preset can be and is why there is no write-and-remote
     * preset here.
     */
    public static final McpToolAnnotations READS_REMOTE = new McpToolAnnotations(true, false, true, true);
}
