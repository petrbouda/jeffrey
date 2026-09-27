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
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapLimits;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectIds;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Json;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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

    private static final MicroscopeView HEAP_OQL_VIEW = MicroscopeView.HEAP_DUMP_OQL;

    /** Between the parts of a run's key; a character no query, count or profile id contains. */
    private static final String KEY_SEPARATOR = "\u0000";

    /**
     * Matches the UI's own OQL page, and the engine clamps to its own ceiling besides. An OQL row is an
     * object a reader then asks a follow-up about, so a long list is rarely the useful answer.
     */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private static final String OQL_TOOL = "heap_oql";
    private static final String PATH_TO_ROOT_TOOL = "heap_getPathToGCRoot";
    private static final String INSTANCE_DETAIL_TOOL = "heap_getInstanceDetail";
    private static final String OPERATIONS_STATUS_TOOL = "operations_status";
    private static final String PROFILE_ID = "profileId";
    private static final String QUERY = "query";
    private static final String LIMIT = "limit";
    private static final String CURSOR = "cursor";
    private static final String INCLUDE_RETAINED_SIZE = "includeRetainedSize";
    private static final String OPERATION_ID = "operationId";

    private static final String LINK_NOTE = "The OQL page opens without this query; paste the query there to "
            + "run it in the UI.";
    private static final String WHY_NEXT_PAGE = "reads the next page of the same query";
    private static final String WHY_PATH = "says why the first row's object is still reachable";
    private static final String WHY_INSTANCE = "opens the first row's object with its fields";
    private static final String WHY_POLL = "reports the query's progress, and its rows once it finishes";
    private static final String WHY_RETRY = "runs the query again, as a new attempt";
    private static final String SQL_GUIDANCE =
            "For a question about the index rather than the object graph — grouping, counting, joining "
                    + "two tables — heap_executeQuery runs SQL against the same dump.";
    private static final String RUNNING_REASON =
            "The dominator tree is being built before the rows can carry retained sizes; on a large dump "
                    + "that takes minutes.";
    private static final String ROW_TOO_LARGE =
            "One OQL row is larger than an answer can carry; select fewer or smaller fields.";

    private final ProfileManager profileManager;
    private final BoundedOperation<OQLQueryResult> retainedRuns;
    private final Supplier<? extends AutoCloseable> backgroundLease;
    private final AdvertisedFamilies advertised;

    /**
     * @param retainedRuns    shared by every call, so a query that builds the dominator tree is run
     *                        once however often it is asked, and operations_status can follow it
     * @param backgroundLease holds this profile open while such a query outlives the call
     * @param advertised      the families this installation serves, which gate the next calls
     */
    public HeapOqlMcpTools(
            ProfileManager profileManager,
            BoundedOperation<OQLQueryResult> retainedRuns,
            Supplier<? extends AutoCloseable> backgroundLease,
            AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.retainedRuns = retainedRuns;
        this.backgroundLease = backgroundLease;
        this.advertised = advertised;
    }

    @Tool(description = "Runs an OQL query against this profile's heap dump - the object query language "
            + "of the Jeffrey UI's OQL page - for object-graph questions SQL cannot put: every "
            + "instance of a type including its subclasses (SELECT * FROM INSTANCEOF java.util.Map), "
            + "the retained set of a selection (SELECT AS RETAINED SET * FROM com.acme.Cache), or a "
            + "filter over an object's own fields (SELECT s FROM java.lang.String s WHERE s.count > "
            + "1000). Rows carry an objectId, a decimal string heap_getInstanceDetail and "
            + "heap_getPathToGCRoot take, and a value cut to " + HeapLimits.VALUE_CHARS + " characters; a page "
            + "that continues hands back nextCursor. For table questions - grouping, counting, joining - "
            + "heap_executeQuery runs SQL against the same index. With includeRetainedSize the call waits "
            + "up to 45 s, then answers status RUNNING with an operationId for operations_status - a client "
            + "that declared the MCP tasks extension gets a task after about 5 s instead.")
    @McpOutputSchema(OqlAnswer.class)
    @McpToolHints(readOnly = false, idempotent = true)
    @McpToolMeta(cost = McpToolCost.SLOW, requires = McpToolRequirement.HEAP_DUMP_INDEXED)
    public McpToolOutcome oql(
            @ToolParam(required = true, description = "The OQL query, e.g. "
                    + "'SELECT * FROM INSTANCEOF java.util.HashMap'")
            String query,
            @ToolParam(required = false, description = "Maximum number of rows to return (default "
                    + DEFAULT_LIMIT + ", maximum " + MAX_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "nextCursor from the previous page, passed unchanged with "
                    + "the same query and includeRetainedSize; omit for the first page")
            String cursor,
            @ToolParam(required = false, description = "Compute the retained size of each result. Off by "
                    + "default because it builds the dominator tree first, which on a large dump takes "
                    + "minutes; the call then waits up to 45 s and answers with an operationId, or with a "
                    + "task after about 5 s for a client that declared the MCP tasks extension")
            Boolean includeRetainedSize,
            McpCallContext call) {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required: an OQL statement to run");
        }
        String statement = query.trim();
        boolean retained = Boolean.TRUE.equals(includeRetainedSize);
        String profileId = profileManager.info().id();
        McpCursor.Filters filters = McpCursor.Filters.of(OQL_TOOL, profileId, statement, retained);
        int offset = OffsetPaging.offset(cursor, filters);
        int pageSize = ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        OQLQueryRequest request = new OQLQueryRequest(statement, pageSize, offset, retained);
        // Everything an answer needs from the request is read here, where it is bound; a worker thread
        // and a task's answer have none.
        Page page = new Page(profileId, statement, retained, pageSize, offset, filters,
                UiLinks.view(profileId, HEAP_OQL_VIEW));

        if (!retained) {
            return McpToolResult.of(present(page, succeeded(profileManager.heapDumpManager().executeQuery(request)),
                    null, null));
        }

        return retainedRuns.run(new BoundedOperation.Work<>(
                        String.join(KEY_SEPARATOR, profileId, statement,
                                String.valueOf(pageSize), String.valueOf(offset)),
                        backgroundLease,
                        () -> succeeded(profileManager.heapDumpManager().executeQuery(request)),
                        result -> present(page, result, null, null),
                        page.call(OQL_TOOL).with(CURSOR, cursor == null || cursor.isBlank() ? null : cursor.trim())
                                .why(WHY_RETRY)),
                call,
                new BoundedOperation.Replies<>(
                        (result, operation) -> present(page, result, operation.operationId(), operation),
                        operation -> running(page, operation)));
    }

    /**
     * The engine reports a parse or compile failure in the result rather than by throwing; the message
     * names the position, which is what lets the model correct its own query. Thrown here — on the
     * worker for a retained query — so the failure reaches the caller the same way either path.
     */
    private static OQLQueryResult succeeded(OQLQueryResult result) {
        if (!result.isSuccess()) {
            throw new ToolExecutionException(result.errorMessage());
        }
        return result;
    }

    /**
     * The rows as the largest page that fits an answer, and the cursor that continues after the last
     * row it carries — which is fewer than the engine returned only when the rows are that large.
     */
    private OqlAnswer present(Page page, OQLQueryResult result, String operationId,
            McpOperationRegistry.Snapshot operation) {
        List<OqlRow> rows = result.results().stream().map(OqlRow::of).toList();
        return FittingPage.largest(rows.size(),
                        shown -> page(page, result, rows.subList(0, shown), shown < rows.size(), operationId, operation),
                        answer -> Json.toString(answer).length() <= McpToolOutput.MAX_CHARS)
                .orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
    }

    private OqlAnswer page(Page page, OQLQueryResult result, List<OqlRow> rows, boolean cut, String operationId,
            McpOperationRegistry.Snapshot operation) {
        McpCursor.Next next = OffsetPaging.next(page.filters(), page.offset(), rows.size(), cut || result.hasMore());
        Optional<String> first = rows.stream().map(OqlRow::objectId).filter(Objects::nonNull).findFirst();
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(next.hasMore(), page.call(OQL_TOOL).with(CURSOR, next.nextCursor()).why(WHY_NEXT_PAGE))
                .nextWhen(first.isPresent(), page.onObject(PATH_TO_ROOT_TOOL, first).why(WHY_PATH))
                .nextWhen(first.isPresent(), page.onObject(INSTANCE_DETAIL_TOOL, first).why(WHY_INSTANCE))
                .guidance(SQL_GUIDANCE)
                .followUp();
        return new OqlAnswer(OqlStatus.OK, null, page.profileId(), page.query(), page.retained(), rows,
                result.totalCount(), next.hasMore(), next.nextCursor(), result.executionTimeMs(), operationId,
                operation, followUp, page.link(), LINK_NOTE);
    }

    private OqlAnswer running(Page page, McpOperationRegistry.Snapshot operation) {
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(McpNextTool.call(OPERATIONS_STATUS_TOOL).with(OPERATION_ID, operation.operationId())
                        .why(WHY_POLL))
                .followUp();
        return new OqlAnswer(OqlStatus.RUNNING, RUNNING_REASON, page.profileId(), page.query(), page.retained(),
                List.of(), null, false, null, null, operation.operationId(), operation, followUp, page.link(),
                LINK_NOTE);
    }

    /** What one call asked for, read on the request thread for whichever thread renders the answer. */
    private record Page(
            String profileId, String query, boolean retained, int limit, int offset, McpCursor.Filters filters,
            String link) {

        /** A call of the same query, as this page asked it. */
        McpNextTool.Call call(String tool) {
            McpNextTool.Call call = McpNextTool.call(tool).with(PROFILE_ID, profileId).with(QUERY, query)
                    .with(LIMIT, limit);
            if (retained) {
                call.with(INCLUDE_RETAINED_SIZE, true);
            }
            return call;
        }

        McpNextTool.Call onObject(String tool, Optional<String> objectId) {
            return McpNextTool.call(tool).with(PROFILE_ID, profileId)
                    .with(HeapObjectIds.PARAMETER, objectId.orElse(null));
        }
    }

    /** Whether the rows are here, or still being computed. */
    public enum OqlStatus {
        OK,
        /** The query builds the dominator tree first and outlasted the wait; the operation follows it. */
        RUNNING
    }

    public record OqlAnswer(
            OqlStatus status,
            @McpNullable
            @McpDescription("Why there are no rows yet; null when they are here")
            String reason,
            String profileId,
            String query,
            boolean includeRetainedSize,
            @McpDescription("The rows of this page; empty while the query runs")
            List<OqlRow> rows,
            @McpNullable
            @McpDescription("How many rows the query matched; null while it runs")
            Integer totalCount,
            @McpDescription("Whether rows remain after these")
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass as cursor, with the same query and includeRetainedSize, to read the next page; "
                    + "null on the last")
            String nextCursor,
            @McpNullable
            Long executionTimeMs,
            @McpNullable
            @McpDescription("The operation computing retained sizes; null for a query that did not need one")
            String operationId,
            @McpNullable
            @McpDescription("That operation as operations_status reports it, without its result, which is the "
                    + "rows here; null for a query that did not need one")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription("The OQL page in the Microscope UI, for the user")
            String uiLink,
            @McpDescription("What the linked page shows instead of this exact answer")
            String uiLinkNote) {
    }

    /**
     * @param objectId null when the row is a computed value rather than an object, in which case there
     *                 is nothing to inspect further — the field is kept so the difference is visible
     */
    public record OqlRow(
            @McpNullable
            @McpDescription("A decimal string, as every heap tool takes it; null for a computed value")
            String objectId,
            @McpNullable
            String className,
            @McpNullable
            @McpDescription("The row's value, cut to " + HeapLimits.VALUE_CHARS + " characters")
            String value,
            long shallowBytes,
            @McpNullable
            @McpDescription("Bytes the object retains; null unless includeRetainedSize was asked for")
            Long retainedBytes) {

        static OqlRow of(OQLResultEntry entry) {
            String value = entry.value();
            if (value != null && value.length() > HeapLimits.VALUE_CHARS) {
                value = value.substring(0, HeapLimits.VALUE_CHARS);
            }
            return new OqlRow(HeapObjectIds.formatNullable(entry.objectId()), entry.className(), value, entry.size(),
                    entry.retainedSize());
        }
    }
}
