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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Everything needed to find one recording session again: which hub, and where inside it.
 * <p>
 * It exists so a model has a <em>single</em> argument to pass. {@code hubs_sessions} prints one ref
 * per row and {@code hubs_download} takes it back, which means the model never assembles a
 * coordinate out of three ids read from three columns and so can never pair a workspace with the
 * wrong project. That is the same bargain {@code profiles_list} strikes with its {@code profileId}.
 * <p>
 * All four parts are carried rather than the session id alone. On the hub a session is keyed by
 * {@code PRIMARY KEY (repository_id, session_id)}, so a session id is not unique by itself, and
 * Microscope needs the project regardless — to resolve a download manager and to write the
 * {@code origin.*} tags that later identify what was downloaded.
 * <p>
 * The encoding is deliberately self-describing rather than a handle into a server-side table. This
 * endpoint holds no per-connection state, so a table would mean a new cache, and a ref would then
 * expire on <em>eviction</em> — a failure far more confusing than a session genuinely removed by
 * retention, and one the reader could do nothing about.
 */
public record HubSessionRef(String hubId, String workspaceId, String projectId, String sessionId) {

    /**
     * Marks the encoding, so a ref from a future scheme can be rejected rather than mis-read.
     */
    private static final String PREFIX = "h1";

    private static final String SEPARATOR = "|";

    private static final int COMPONENTS = 4;

    /**
     * What a reader is told to do rather than trying to repair the string themselves.
     */
    private static final String RECOVERY =
            " Pass the session_ref exactly as hubs_sessions printed it, without quotes or truncation.";

    public HubSessionRef {
        hubId = required(hubId, "hub id");
        workspaceId = required(workspaceId, "workspace id");
        projectId = required(projectId, "project id");
        sessionId = required(sessionId, "session id");

        // Only the session id may contain a separator, because only it is decoded from the tail.
        // The other three are minted by Jeffrey (a UUIDv7, or cfg-<key> for a configured hub), so
        // this rejects the impossible at the point it would be introduced rather than letting a
        // ref that cannot survive a round trip escape into a model's context.
        rejectSeparator(hubId, "hub id");
        rejectSeparator(workspaceId, "workspace id");
        rejectSeparator(projectId, "project id");
    }

    /**
     * The ref as it appears in a table cell and travels back as a tool argument. Base64-URL is
     * {@code [A-Za-z0-9_-]}: no pipe to split a Markdown column, no whitespace to be trimmed away,
     * nothing that needs escaping inside a JSON string.
     */
    public String encode() {
        String joined = String.join(SEPARATOR, hubId, workspaceId, projectId, sessionId);
        return PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads back a ref produced by {@link #encode()}.
     *
     * @throws IllegalArgumentException when the ref is not one of ours, naming the step that
     *                                  produces a valid one — the message is the model's only route
     *                                  back to a working call
     */
    public static HubSessionRef decode(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("A session_ref is required." + RECOVERY);
        }

        String trimmed = ref.trim();
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a session_ref: " + trimmed + "." + RECOVERY);
        }

        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Damaged session_ref: " + trimmed + "." + RECOVERY);
        }

        // Limit COMPONENTS keeps a separator inside the session id, which is the one part that comes
        // from outside Jeffrey and therefore the one part whose contents cannot be relied on.
        String[] parts = new String(decoded, StandardCharsets.UTF_8).split("\\" + SEPARATOR, COMPONENTS);
        if (parts.length != COMPONENTS) {
            throw new IllegalArgumentException("Incomplete session_ref: " + trimmed + "." + RECOVERY);
        }

        try {
            return new HubSessionRef(parts[0], parts[1], parts[2], parts[3]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Incomplete session_ref: " + trimmed + "." + RECOVERY);
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A session reference needs a " + what + ".");
        }
        return value;
    }

    private static void rejectSeparator(String value, String what) {
        if (value.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    "A " + what + " must not contain '" + SEPARATOR + "': " + value);
        }
    }
}
