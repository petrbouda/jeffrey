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
 * The behavioural hints a client shows a user before approving a tool call.
 * <p>
 * MCP calls these {@code annotations} on a tool. They exist so a reader can tell a tool that only looks
 * at a profile from one that imports a file or pulls a recording off a hub, without having to read
 * every description. Jeffrey has nine writers among a hundred-odd tools, so the honest default is
 * read-only and the exceptions declare themselves with {@link McpToolHints}. The set is pinned by
 * {@code McpToolsetAssemblerTest}, so a new writer that forgets its hint fails a test rather than
 * quietly claiming to read.
 * <p>
 * {@code destructive} is true on exactly one tool, {@code recordings_delete}, which takes a recording
 * and the profile built from it away; nothing else Jeffrey exposes removes anything.
 * {@code openWorld} marks the tools that reach past this installation — the hub family talks to
 * machines this Jeffrey merely knows about, and the IDE family to an editor running beside it. What
 * either answers is outside Jeffrey's control.
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
     * What almost every Jeffrey tool is: it reads one profile and reports what it found.
     */
    public static final McpToolAnnotations READ_ONLY = new McpToolAnnotations(true, false, true, false);

    /**
     * A tool that creates something — a profile from a recording file, a local copy of a hub session.
     * Not destructive: it adds rather than replaces, and Jeffrey's creating tools return what already
     * exists rather than building it twice, which is what makes them idempotent.
     */
    public static final McpToolAnnotations CREATES = new McpToolAnnotations(false, false, true, false);

    /**
     * A tool that reads, but reads from something outside this installation — another machine, or
     * another process on this one.
     * <p>
     * A family is described by what most of it does. The one tool in such a family that writes —
     * pulling a hub recording down, opening a file in the editor — says so for itself with
     * {@link McpToolHints}, which is finer-grained than a family preset can be and is why there is no
     * write-and-remote preset here.
     */
    public static final McpToolAnnotations READS_REMOTE = new McpToolAnnotations(true, false, true, true);
}
