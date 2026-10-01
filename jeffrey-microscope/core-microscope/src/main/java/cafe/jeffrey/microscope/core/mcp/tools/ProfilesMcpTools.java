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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopePage;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.mcp.JeffreyMcpServer;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The installation catalogue, traversed by immutable profile id instead of a shifting row offset. */
public class ProfilesMcpTools {

    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_LIST_LIMIT = 1000;
    private static final int MAX_NAME_CHARS = 256;
    private static final String TOOL_NAME = "profiles_list";
    private static final String RECORDINGS_STATUS = "recordings_status";
    private static final String SEARCH = "search";
    private static final String LIMIT = "limit";
    private static final String CURSOR = "cursor";
    private static final String RECORDING_ID = "recordingId";
    private static final String NEXT_PAGE_WHY = "continues the catalogue past this page";
    private static final String BUILDING_WHY = "reports how far the build of the newest listed profile still being built has got";
    private static final String QUICK_ANALYSIS_PROJECT = "(quick analysis)";
    private static final String BUILDING_NOTE =
            "A profile listed as `building` is still being parsed. Pass its recording_id to recordings_status "
                    + "to check progress.";
    private static final String NO_PROFILES =
            "No profiles have been analysed yet. Upload a JFR recording or heap dump in Jeffrey and run Analyze first.";
    private static final String NO_MATCH = "No profile matches the search.";
    /** Whether a listed profile can be analysed yet, or is still being built from its recording. */
    enum Readiness {
        YES,
        BUILDING
    }

    /**
     * One catalogue row. Time is UTC epoch milliseconds; the readable ISO forms stay in the text table.
     */
    record ProfileRow(
            String profileId,
            @McpNullable
            @McpDescription("Use with recordings_status while building")
            String recordingId,
            @McpNullable
            @McpDescription("Bounded display name, at most 256 characters; see nameTruncated")
            String name,
            boolean nameTruncated,
            @McpNullable
            String projectId,
            @McpNullable
            String workspaceId,
            @McpNullable
            String eventSource,
            @McpNullable
            @McpDescription("When the recording started, as UTC epoch milliseconds; null when unknown")
            Long recordedEpochMs,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("Recording span in milliseconds; null when the recording span is unknown")
            Long durationMs,
            Readiness ready,
            boolean modified) {
    }

    /** A page of the catalogue, which a cursor continues past its last row. */
    record ProfilePage(
            CatalogueStatus status,
            @McpNullable
            @McpDescription("Why nothing is listed; null when status is OK")
            String reason,
            List<ProfileRow> profiles,
            @McpMinimum(0)
            int returned,
            @McpMinimum(0)
            @McpDescription("Current matching catalogue size, including earlier pages")
            int total,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged with the same search; null at the end")
            String nextCursor,
            @McpDescription("Always true: every returned row is fully represented; hasMore describes catalogue continuation")
            boolean complete,
            McpFollowUp followUp,
            @McpDescription("The recordings page in the Microscope UI, which lists every profile, for the user")
            String uiLink) {
    }

    private final MicroscopeCoreRepositories coreRepositories;
    private final AdvertisedFamilies advertised;

    public ProfilesMcpTools(MicroscopeCoreRepositories coreRepositories, AdvertisedFamilies advertised) {
        this.coreRepositories = coreRepositories;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the analysed profiles, newest first, each with the profileId the "
            + "analysis tools take; a profile still being built carries a recordingId for "
            + "recordings_status. Up to 100 rows by default, at most 1000, further bounded by "
            + "response size: follow nextCursor with the same search until hasMore=false. A live "
            + "catalogue - a profile added before the cursor needs a fresh traversal. Long names are "
            + "shortened and flagged nameTruncated; identifiers never are.")
    @McpOutputSchema(ProfilePage.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult list(
            @ToolParam(required = false, description = "Case-insensitive substring of the full profile name") String search,
            @ToolParam(required = false, description = "Maximum rows in this page (default " + DEFAULT_LIST_LIMIT
                    + ", maximum " + MAX_LIST_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_LIST_LIMIT, min = 1, max = MAX_LIST_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the preceding page with the same search") String cursor) {
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        McpCursor.Filters filters = McpCursor.Filters.of(TOOL_NAME, normalizedSearch);
        String afterId = cursor == null ? null : JeffreyMcpServer.CURSOR.decodeKeyset(cursor, filters, ProfilesMcpTools::afterProfileId);
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
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        int selected = Math.min(remaining.size(), rows);
        PageRequest request = new PageRequest(search, normalizedSearch, rows, filters, UiLinks.page(MicroscopePage.RECORDINGS));
        // Both representations are measured before publication. Never cap a rendered table or trim
        // its JSON independently: the cursor must advance exactly past the rows the client received.
        return FittingPage.largest(selected,
                        count -> renderPage(remaining.subList(0, count), matching.size(), remaining.size(), request),
                        Page::fits)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A profile's identifiers exceed the response size limit; its row cannot be returned intact."))
                .result();
    }

    private Page renderPage(List<ProfileInfo> profiles, int total, int remaining, PageRequest request) {
        String search = request.normalizedSearch();
        boolean hasMore = profiles.size() < remaining;
        String nextCursor = hasMore
                ? JeffreyMcpServer.CURSOR.encode(request.filters(), new McpCursor.Keyset(List.of(profiles.getLast().id()))) : null;
        List<ProfileRow> rows = new ArrayList<>(profiles.size());
        MarkdownTable table = MarkdownTable.withColumns("profile_id", "recording_id", "name", "project",
                "event source", "recorded", "duration", "ready", "modified");
        for (ProfileInfo profile : profiles) {
            String name = displayName(profile.name());
            boolean nameTruncated = profile.name() != null && profile.name().length() > MAX_NAME_CHARS;
            Instant started = profile.profilingStartedAt();
            boolean spanKnown = started != null && profile.profilingFinishedAt() != null;
            String source = profile.eventSource() == null ? null : profile.eventSource().toString();
            Readiness ready = profile.enabled() ? Readiness.YES : Readiness.BUILDING;
            rows.add(new ProfileRow(profile.id(), profile.recordingId(), name, nameTruncated,
                    profile.projectId(), profile.workspaceId(), source,
                    started == null ? null : started.toEpochMilli(),
                    spanKnown ? profile.duration().toMillis() : null,
                    ready, profile.modified()));
            table.row(profile.id(), profile.recordingId(), name,
                    profile.projectId() == null ? QUICK_ANALYSIS_PROJECT : profile.projectId(),
                    source, started == null ? null : started.toString(),
                    spanKnown ? profile.duration().toString() : null,
                    ready.name().toLowerCase(Locale.ROOT), profile.modified() ? "yes" : "no");
        }
        String building = rows.stream()
                .filter(row -> row.ready() == Readiness.BUILDING && row.recordingId() != null)
                .map(ProfileRow::recordingId)
                .findFirst()
                .orElse(null);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(hasMore, NextCalls.to(TOOL_NAME)
                        .with(SEARCH, request.search())
                        .with(LIMIT, request.limit())
                        .with(CURSOR, nextCursor)
                        .why(NEXT_PAGE_WHY))
                .nextWhen(building != null, NextCalls.to(RECORDINGS_STATUS)
                        .with(RECORDING_ID, building)
                        .why(BUILDING_WHY))
                .followUp();
        String emptyReason = total > 0 ? null : search.isEmpty() ? NO_PROFILES : NO_MATCH;
        ProfilePage structured = new ProfilePage(
                emptyReason == null ? CatalogueStatus.OK : CatalogueStatus.EMPTY,
                emptyReason,
                List.copyOf(rows), profiles.size(), total, hasMore, nextCursor, true, followUp, request.uiLink());
        String text = "Returned " + profiles.size() + " of " + total + " matching profiles.\n\n";
        if (profiles.isEmpty()) {
            text += emptyReason == null ? "No profiles remain after this cursor." : emptyReason;
        } else {
            text += table.note("Names longer than 256 characters are shortened with an ellipsis; nameTruncated marks them in structuredContent.")
                    .note("A `modified` profile has had frames renamed or collapsed, so its frame names may differ from the source code.")
                    .note(advertised.hint(AdvertisedFamilies.RECORDINGS, BUILDING_NOTE))
                    .renderUncapped();
        }
        // The next page is named once, by the footer's Next: line, which carries the cursor.
        text += hasMore
                ? "\n\nMore profiles are available (hasMore=true)."
                : "\n\nEnd of the matching catalogue (hasMore=false).";
        // The page is measured whole, footer included, and never cut: the footer is appended, not capped.
        text += LinkedOutput.footer(followUp, request.uiLink(), null);
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

    /**
     * The profile id a keyset cursor continues after: exactly one, and not blank.
     */
    private static String afterProfileId(McpCursor.Keyset keyset) {
        List<String> after = keyset.after();
        if (after.size() != 1 || after.getFirst() == null || after.getFirst().isBlank()) {
            throw new IllegalArgumentException("a profiles_list cursor holds one profile id: after=" + after);
        }
        return after.getFirst();
    }

    /**
     * What one call asked for, carried into every candidate page the size search renders.
     *
     * @param search           the search as the caller spelled it, which the next page repeats
     * @param normalizedSearch the search the rows are matched and the cursor is bound to
     * @param limit            the most rows a page holds
     */
    private record PageRequest(
            String search, String normalizedSearch, int limit, McpCursor.Filters filters, String uiLink) {
    }

    private record Page(String text, ProfilePage structured) {
        boolean fits() {
            return text.length() <= McpToolOutput.MAX_CHARS && Json.toString(structured).length() <= McpToolOutput.MAX_CHARS;
        }

        McpToolResult result() {
            return McpToolResult.of(text, structured);
        }
    }
}
