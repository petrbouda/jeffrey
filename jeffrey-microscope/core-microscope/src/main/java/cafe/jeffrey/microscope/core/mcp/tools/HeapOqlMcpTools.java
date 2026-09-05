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

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;

/**
 * OQL over a heap dump — the object query language the Jeffrey UI's OQL page runs.
 * <p>
 * The {@code heap_} family already carries SQL against the index, and this is not a second way to do
 * the same thing. SQL asks table questions of the index as it is stored: join {@code instance} to
 * {@code class}, group by loader, count what a column holds. OQL asks object questions of the graph
 * the index only implies — every instance of a type including its subclasses, the retained set of a
 * selection, the reference path that holds something alive. Each is clumsy in the other's language,
 * and the retained-set and {@code instanceof} forms have no SQL spelling at all.
 * <p>
 * Registered under the {@code heap} prefix beside {@code HeapDumpMcpTools} and {@code HeapDiffMcpTools}:
 * one family, three classes, because the prefix names what a reader is asking about rather than which
 * class answers.
 */
public class HeapOqlMcpTools {

    private static final String HEAP_OQL_VIEW = "oql";
    private static final String QUERY_PARAM = "query";

    /**
     * Matches the UI's own OQL page, and the engine clamps to its own ceiling besides. An OQL row is an
     * object a reader then asks a follow-up about, so a long list is rarely the useful answer.
     */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private static final String STEP_RETAINED =
            "heap_getPathToGCRoot on an objectId from these rows says why it is still reachable, which "
                    + "is what turns a large object into a leak claim.";
    private static final String STEP_SQL =
            "For a question about the index rather than the object graph — grouping, counting, joining "
                    + "two tables — heap_executeQuery runs SQL against the same dump.";

    private final ProfileManager profileManager;

    public HeapOqlMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "Run an OQL query against this profile's heap dump — the object query language "
            + "of the Jeffrey UI's OQL page. Use it for questions about the object graph that SQL "
            + "cannot put: every instance of a type including its subclasses "
            + "(SELECT * FROM INSTANCEOF java.util.Map), the retained set of a selection "
            + "(SELECT AS RETAINED SET * FROM com.acme.Cache), or a filter over an object's own fields "
            + "(SELECT s FROM java.lang.String s WHERE s.count > 1000). Results carry an objectId that "
            + "heap_getInstanceDetail and heap_getPathToGCRoot take. For table questions — grouping, "
            + "counting, joining — use heap_executeQuery, which runs SQL against the same index.")
    public String oql(
            @ToolParam(required = true, description = "The OQL query, e.g. "
                    + "'SELECT * FROM INSTANCEOF java.util.HashMap'")
            String query,
            @ToolParam(required = false, description = "Maximum number of rows to return (default 50, "
                    + "maximum 100)")
            Integer limit,
            @ToolParam(required = false, description = "Compute the retained size of each result. Off by "
                    + "default because it builds the dominator tree first, which on a large dump takes "
                    + "minutes")
            Boolean includeRetainedSize) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required: an OQL statement to run");
        }
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        OQLQueryResult result = profileManager.heapDumpManager().executeQuery(new OQLQueryRequest(
                query.trim(), effectiveLimit, 0, Boolean.TRUE.equals(includeRetainedSize)));

        // The engine reports a parse or compile failure in the result rather than by throwing, and the
        // message names the position — which is what lets the model correct its own query.
        if (!result.isSuccess()) {
            return McpToolOutput.error(result.errorMessage());
        }
        return LinkedOutput.json(new OqlResult(
                result.results().stream().map(OqlRow::of).toList(),
                result.totalCount(),
                result.hasMore(),
                result.executionTimeMs(),
                nextSteps(),
                UiLinks.view(profileManager.info().id(), HEAP_OQL_VIEW,
                        Map.of(QUERY_PARAM, query.trim()))));
    }

    private static List<String> nextSteps() {
        return NextSteps.builder()
                .add(STEP_RETAINED)
                .add(STEP_SQL)
                .build();
    }

    private record OqlResult(
            List<OqlRow> rows,
            int totalCount,
            boolean hasMore,
            long executionTimeMs,
            List<String> nextSteps,
            String uiLink) {
    }

    /**
     * @param objectId null when the row is a computed value rather than an object, in which case there
     *                 is nothing to inspect further — the field is kept so the difference is visible
     */
    private record OqlRow(Long objectId, String className, String value, long shallowBytes, Long retainedBytes) {

        static OqlRow of(OQLResultEntry entry) {
            return new OqlRow(
                    entry.objectId(), entry.className(), entry.value(), entry.size(), entry.retainedSize());
        }
    }
}
