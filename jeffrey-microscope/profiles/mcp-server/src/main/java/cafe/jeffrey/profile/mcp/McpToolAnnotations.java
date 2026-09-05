/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cafe.jeffrey.profile.mcp;

/**
 * The behavioural hints a client shows a user before approving a tool call.
 * <p>
 * MCP calls these {@code annotations} on a tool. They exist so a reader can tell a tool that only looks
 * at a profile from one that imports a file or pulls a recording off a hub, without having to read
 * every description. Jeffrey has three writers among a hundred-odd tools, so the honest default is
 * read-only and the exceptions declare themselves with {@link McpToolHints}.
 * <p>
 * {@code destructive} is false throughout: nothing Jeffrey exposes deletes a profile or a recording.
 * {@code openWorld} marks the tools that reach past this installation — the hub family talks to
 * machines this Jeffrey merely knows about, and what they answer is outside its control.
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
     * Not destructive: it adds rather than replaces, and both of Jeffrey's writers return what already
     * exists rather than building it twice, which is what makes them idempotent.
     */
    public static final McpToolAnnotations CREATES = new McpToolAnnotations(false, false, true, false);

    /**
     * A tool that both writes and reaches another machine — the hub family.
     */
    public static final McpToolAnnotations CREATES_REMOTE = new McpToolAnnotations(false, false, true, true);

    /**
     * A tool that reads, but reads from another machine.
     */
    public static final McpToolAnnotations READS_REMOTE = new McpToolAnnotations(true, false, true, true);
}
