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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** The installation catalogue, traversed by immutable profile id instead of a shifting row offset. */
public class ProfilesMcpTools {

    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 1000;
    private static final int MAX_NAME_CHARS = 256;
    private static final int CURSOR_VERSION = 1;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String QUICK_ANALYSIS_PROJECT = "(quick analysis)";
    private static final String INVALID_CURSOR =
            "Invalid cursor or cursor belongs to a different search. Restart profiles_list without cursor.";
    private static final String NO_PROFILES =
            "No profiles have been analysed yet. Upload a JFR recording or heap dump in Jeffrey and run Analyze first.";
    private static final String OUTPUT_SCHEMA = """
            {
              "type":"object",
              "properties":{
                "profiles":{"type":"array","items":{
                  "type":"object",
                  "properties":{
                    "profileId":{"type":"string"},
                    "recordingId":{"type":["string","null"],"description":"Use with recordings_status while building"},
                    "name":{"type":["string","null"],"maxLength":256,"description":"Bounded display name; see nameTruncated"},
                    "nameTruncated":{"type":"boolean"},
                    "projectId":{"type":["string","null"]},
                    "workspaceId":{"type":["string","null"]},
                    "eventSource":{"type":["string","null"]},
                    "recorded":{"type":["string","null"]},
                    "duration":{"type":["string","null"]},
                    "ready":{"type":"string","enum":["yes","building"]},
                    "modified":{"type":"boolean"}
                  },
                  "required":["profileId","recordingId","name","nameTruncated","projectId","workspaceId",
                              "eventSource","recorded","duration","ready","modified"],
                  "additionalProperties":false
                }},
                "returned":{"type":"integer","minimum":0},
                "total":{"type":"integer","minimum":0,"description":"Current matching catalogue size, including earlier pages"},
                "hasMore":{"type":"boolean"},
                "nextCursor":{"type":["string","null"],"description":"Pass unchanged with the same search; null at the end"},
                "complete":{"type":"boolean","const":true,"description":"Every returned row is fully represented; hasMore describes catalogue continuation"}
              },
              "required":["profiles","returned","total","hasMore","nextCursor","complete"],
              "additionalProperties":false
            }
            """;

    private final MicroscopeCoreRepositories coreRepositories;

    public ProfilesMcpTools(MicroscopeCoreRepositories coreRepositories) {
        this.coreRepositories = coreRepositories;
    }

    /** Source compatibility for Java callers; MCP advertises only the cursor-aware overload. */
    public String list(String search, Integer limit) {
        return list(search, limit, null).text();
    }

    @Tool(description = "List analysed profiles, newest first, with structured cursor pagination. "
            + "Returns up to 100 rows by default, at most 1000, further bounded by response size. "
            + "Follow nextCursor with the same search until hasMore=false to traverse the catalogue. "
            + "This is a live catalogue: additions before the cursor require a fresh traversal. "
            + "Names are bounded display values with nameTruncated; identifiers remain unchanged. "
            + "Start here: analysis tools use profileId. Building profiles have a recordingId for recordings_status.")
    @McpOutputSchema(OUTPUT_SCHEMA)
    public McpToolResult list(
            @ToolParam(required = false, description = "Case-insensitive substring of the full profile name") String search,
            @ToolParam(required = false, description = "Maximum rows in this page (default 100, maximum 1000)") Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the preceding page with the same search") String cursor) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String fingerprint = fingerprint(normalizedSearch);
        String afterId = cursor == null ? null : Cursor.decode(cursor, fingerprint).lastProfileId();
        // Newest first, which is the order the catalogue is read in: the recording somebody just
        // imported is the one they are asking about. A profile id is a UUIDv7, so it sorts by creation
        // time and descending id is both that order and a single-column keyset the cursor can carry --
        // paging by a timestamp would need a composite cursor to break ties that ids do not have.
        List<ProfileInfo> matching = coreRepositories.findAllProfiles().stream()
                .filter(profile -> matches(profile, normalizedSearch))
                .sorted(Comparator.comparing(ProfileInfo::id).reversed())
                .toList();
        List<ProfileInfo> remaining = matching.stream()
                .filter(profile -> afterId == null || profile.id().compareTo(afterId) < 0)
                .toList();
        int selected = Math.min(remaining.size(), ToolArguments.boundedLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT));
        Page page = renderPage(remaining.subList(0, selected), matching.size(), remaining.size(), normalizedSearch, fingerprint);
        if (page.fits()) {
            return page.result();
        }

        // Both representations are measured before publication. Never cap a rendered table or trim
        // its JSON independently: the cursor must advance exactly past the rows the client received.
        int low = 1;
        int high = selected - 1;
        Page fitting = null;
        while (low <= high) {
            int count = low + (high - low) / 2;
            Page candidate = renderPage(remaining.subList(0, count), matching.size(), remaining.size(), normalizedSearch, fingerprint);
            if (candidate.fits()) {
                fitting = candidate;
                low = count + 1;
            } else {
                high = count - 1;
            }
        }
        if (fitting == null) {
            throw new IllegalArgumentException("A profile's identifiers exceed the response size limit; its row cannot be returned intact.");
        }
        return fitting.result();
    }

    private static Page renderPage(List<ProfileInfo> profiles, int total, int remaining, String search, String fingerprint) {
        boolean hasMore = profiles.size() < remaining;
        String nextCursor = hasMore ? new Cursor(fingerprint, profiles.getLast().id()).encode() : null;
        ObjectNode structured = Json.createObject();
        var rows = structured.putArray("profiles");
        MarkdownTable table = MarkdownTable.withColumns("profile_id", "recording_id", "name", "project",
                "event source", "recorded", "duration", "ready", "modified");
        for (ProfileInfo profile : profiles) {
            String name = displayName(profile.name());
            boolean nameTruncated = profile.name() != null && profile.name().length() > MAX_NAME_CHARS;
            String recorded = profile.profilingStartedAt() == null ? null : profile.profilingStartedAt().toString();
            String duration = profile.profilingStartedAt() == null || profile.profilingFinishedAt() == null
                    ? null : profile.duration().toString();
            String source = profile.eventSource() == null ? null : profile.eventSource().toString();
            String ready = profile.enabled() ? "yes" : "building";
            rows.addObject()
                    .put("profileId", profile.id()).put("recordingId", profile.recordingId())
                    .put("name", name).put("nameTruncated", nameTruncated)
                    .put("projectId", profile.projectId()).put("workspaceId", profile.workspaceId())
                    .put("eventSource", source).put("recorded", recorded).put("duration", duration)
                    .put("ready", ready).put("modified", profile.modified());
            table.row(profile.id(), profile.recordingId(), name,
                    profile.projectId() == null ? QUICK_ANALYSIS_PROJECT : profile.projectId(),
                    source, recorded, duration, ready, profile.modified() ? "yes" : "no");
        }
        structured.put("returned", profiles.size()).put("total", total).put("hasMore", hasMore)
                .put("nextCursor", nextCursor).put("complete", true);
        String text = "Returned " + profiles.size() + " of " + total + " matching profiles.\n\n";
        if (profiles.isEmpty()) {
            text += total > 0 ? "No profiles remain after this cursor."
                    : search.isEmpty() ? NO_PROFILES : "No profile matches the search.";
        } else {
            text += table.note("Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.")
                    .note("A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.")
                    .note("A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status to check progress.")
                    .renderUncapped();
        }
        if (hasMore) {
            text += "\n\nMore profiles are available. Call profiles_list with the same search and nextCursor: `" + nextCursor + "`.";
            if (search.isEmpty()) {
                text += "\nContinuation resource: jeffrey://profiles?cursor=" + nextCursor;
            }
        } else {
            text += "\n\nEnd of the matching catalogue (hasMore=false).";
        }
        return new Page(text, structured);
    }

    private static String displayName(String name) {
        if (name == null || name.length() <= MAX_NAME_CHARS) {
            return name;
        }
        int end = MAX_NAME_CHARS - 1;
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end) + "…";
    }

    private static boolean matches(ProfileInfo profile, String search) {
        return search.isEmpty() || (profile.name() != null && profile.name().toLowerCase(Locale.ROOT).contains(search));
    }

    private static String fingerprint(String search) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(search.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 algorithm unavailable", e);
        }
    }

    private record Page(String text, ObjectNode structured) {
        boolean fits() {
            return text.length() <= McpToolOutput.MAX_CHARS && Json.toString(structured).length() <= McpToolOutput.MAX_CHARS;
        }

        McpToolResult result() {
            return new McpToolResult(text, structured);
        }
    }

    private record Cursor(String searchFingerprint, String lastProfileId) {
        String encode() {
            ObjectNode value = Json.createObject().put("version", CURSOR_VERSION)
                    .put("search", searchFingerprint).put("after", lastProfileId);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Json.toByteArray(value));
        }

        static Cursor decode(String token, String fingerprint) {
            try {
                if (token.isEmpty() || token.length() > McpToolOutput.MAX_CHARS) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }
                ObjectNode value = Json.readObjectNode(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8));
                if (value == null || value.size() != 3 || !value.path("version").isInt()
                        || value.path("version").asInt() != CURSOR_VERSION || !value.path("search").isString()
                        || !value.path("after").isString() || value.path("after").asString().isEmpty()
                        || !fingerprint.equals(value.path("search").asString())) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }
                Cursor cursor = new Cursor(fingerprint, value.path("after").asString());
                if (!cursor.encode().equals(token)) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }
                return cursor;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(INVALID_CURSOR, e);
            }
        }
    }
}
