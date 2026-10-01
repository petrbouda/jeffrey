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
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Key;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Keys;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.KeysStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Match;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Search;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.SearchStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Value;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.Values;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceAttributeAnswers.ValuesStatus;
import cafe.jeffrey.microscope.core.mcp.tools.traces.TraceLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The business dimensions a trace carried: which tenant, which customer, which feature flag.
 * <p>
 * Every other {@code traces_} tool aggregates by <em>operation</em> — the shape of the request. That
 * cannot answer the question most latency investigations actually end at, which is that one
 * population is slow and the rest is fine: the same endpoint, fast for almost everyone and terrible
 * for one tenant, has a healthy average and a p99 nobody can locate. Attributes are how a trace says
 * which population it belonged to, and this family groups by them.
 */
public class TraceAttributesMcpTools {

    private static final MicroscopeView SEARCH_VIEW = MicroscopeView.TRACES_ATTRIBUTE_SEARCH;
    private static final MicroscopeView VALUES_VIEW = MicroscopeView.TRACES_ATTRIBUTE_VALUES;
    private static final String KEY_PARAM = "key";
    private static final String SOURCE_PARAM = "source";
    private static final String OWNER_PARAM = "owner";
    private static final String EVENT_TYPE_PARAM = "eventType";
    private static final String WHERE_PARAM = "where";
    private static final String SCOPE_PARAM = "scope";
    /** Between a condition's fields in the search page's where=; the page's encodeCondition spells it so. */
    private static final String CONDITION_SEPARATOR = "~";

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 200;

    private static final String NO_ATTRIBUTES =
            "This profile carries no trace attributes. They come from the Jeffrey tracing "
                    + "instrumentation recording them on a span; a recording with traces but no "
                    + "attributes has nothing to group by here. traces_operations still groups by "
                    + "operation.";

    private static final String NO_SUCH_KEY =
            "No attribute key '%s' was recorded in this scope. traces_attributeKeys lists the keys this "
                    + "profile holds, each with the source and owner that identify it.";

    private static final String NOTHING_MATCHED =
            "No trace matched this condition. traces_attributeValues lists the values that were actually "
                    + "recorded for the key, and traces_attributeKeys the keys themselves.";

    private static final String ROW_TOO_LARGE =
            "A single row of this list exceeds the answer size limit; narrow the question.";

    private static final String VALUES_WHY =
            "breaks the key into its values with a latency profile each, where a slow population separates";
    private static final String OPERATIONS_WHY = "the traces grouped by operation instead";
    private static final String SEARCH_WHY = "the individual traces carrying the costliest value";
    private static final String KEYS_WHY = "the keys this profile holds, each with its source and owner";
    private static final String EXPORT_WHY = "the slowest matching trace span by span, which says why";
    private static final String NEXT_PAGE_WHY = "the next page of matching traces";
    private static final String NOT_THE_CAUSE_GUIDANCE =
            "An attribute says which population was slow, never why. The span tree of one of its "
                    + "traces says why.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public TraceAttributesMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the attribute keys this recording's traces carried - tenant, customer, "
            + "feature flag, whatever the application recorded on its spans - each with how many "
            + "distinct values and traces it covers. A key is identified by the triple (source, "
            + "owner, key), which the other attribute tools take. status NO_ATTRIBUTES: the traces "
            + "carried none.")
    @McpOutputSchema(Keys.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult attributeKeys(
            @ToolParam(required = false, description = "Optional event type to restrict the keys to, e.g. "
                    + "'jeffrey.HttpServerExchange'. Omit for every key in the profile.")
            String eventType) {

        String scope = blankToNull(eventType);
        List<TraceAttributeKeyRow> rows = scope == null
                ? profileManager.traceAttributesManager().keys()
                : profileManager.traceAttributesManager().keysOf(scope);
        String uiLink = UiLinks.view(profileId(), SEARCH_VIEW);

        if (rows.isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(call(FollowUpCalls.TRACES_OPERATIONS).why(OPERATIONS_WHY))
                    .followUp();
            return McpToolResult.of(new Keys(KeysStatus.NO_ATTRIBUTES, NO_ATTRIBUTES, profileId(), scope,
                    List.of(), null, followUp, uiLink));
        }

        List<Key> all = rows.stream().map(Key::of).toList();
        Keys answer = FittingPage.largest(all.size(), shown -> {
            List<Key> keys = List.copyOf(all.subList(0, shown));
            NextSteps.Builder steps = NextSteps.builder(advertised);
            keys.stream().filter(key -> !key.searchOnly()).findFirst()
                    .ifPresent(key -> steps.next(keyCall(FollowUpCalls.TRACES_ATTRIBUTE_VALUES,
                            key.key(), key.source(), key.owner()).with(FollowUpCalls.EVENT_TYPE, scope).why(VALUES_WHY)));
            return new Keys(KeysStatus.OK, null, profileId(), scope, keys, all.size() - shown, steps.followUp(), uiLink);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Breaks one attribute key into its values, each with how many traces carried it "
            + "and that value's own latency in nanoseconds - total, p50, p95, max - and error count. "
            + "Answers 'it is slow for one customer': a value whose p95 stands apart from the rest "
            + "names the population, which an operation-level percentile averages away. Trace counts "
            + "do not sum to the profile's total, because a trace whose spans recorded two values "
            + "counts under both. omittedValues counts the values left out by limit. status "
            + "NO_VALUES: the key was not recorded in this scope.")
    @McpOutputSchema(Values.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult attributeValues(
            @ToolParam(required = true, description = "The attribute key name, from traces_attributeKeys")
            String key,
            @ToolParam(required = false, description = "Where the key comes from, as traces_attributeKeys reported it: "
                    + "ATTRIBUTE (the default), EVENT_FIELD, SPAN_SHAPE, NOTIFICATION_ATTRIBUTE or "
                    + "NOTIFICATION_SHAPE")
            TraceAttributeSource source,
            @ToolParam(required = false, description = "The owner from traces_attributeKeys - for an EVENT_FIELD this is "
                    + "the event type declaring it, and it is required there. Omit when the key row "
                    + "showed none.")
            String owner,
            @ToolParam(required = false, description = "Optional event type to restrict to")
            String eventType,
            @ToolParam(required = false, description = "Order by one of: TOTAL_TIME (default), TRACES, P50, P95, MAX, "
                    + "ERRORS, VALUE")
            TraceAttributeValueSortField sort,
            @ToolParam(required = false, description = "Maximum number of values to return (default "
                    + DEFAULT_LIMIT + ", maximum " + MAX_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit) {

        TraceAttributeKeyId keyId = keyId(key, source, owner);
        String scope = blankToNull(eventType);
        TraceAttributeValueQuery query = new TraceAttributeValueQuery(
                keyId,
                orDefault(sort, TraceAttributeValueSortField.TOTAL_TIME),
                true,
                ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT),
                scope);
        String uiLink = UiLinks.view(profileId(), VALUES_VIEW, keyQuery(keyId, scope));

        TraceAttributeValues values = profileManager.traceAttributesManager().values(query);
        if (values.values().isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(call(FollowUpCalls.TRACES_ATTRIBUTE_KEYS).with(FollowUpCalls.EVENT_TYPE, scope).why(KEYS_WHY))
                    .followUp();
            return McpToolResult.of(new Values(ValuesStatus.NO_VALUES, NO_SUCH_KEY.formatted(keyId.key()),
                    profileId(), keyId.key(), keyId.source(), keyId.owner(), scope, List.of(), values.distinctValues(),
                    null, values.tracesWithoutKey(), followUp, uiLink));
        }

        List<Value> all = values.values().stream().map(Value::of).toList();
        Values answer = FittingPage.largest(all.size(), shown -> {
            List<Value> rows = List.copyOf(all.subList(0, shown));
            NextSteps.Builder steps = NextSteps.builder(advertised);
            rows.stream().findFirst()
                    .ifPresent(value -> steps.next(keyCall(FollowUpCalls.TRACES_ATTRIBUTE_SEARCH,
                            keyId.key(), keyId.source(), keyId.owner())
                            .with(FollowUpCalls.OPERATOR, TraceAttributeOperator.EQ)
                            .with(FollowUpCalls.VALUE, value.value())
                            .why(SEARCH_WHY)));
            steps.guidance(NOT_THE_CAUSE_GUIDANCE);
            return new Values(ValuesStatus.OK, null, profileId(), keyId.key(), keyId.source(), keyId.owner(), scope,
                    rows, values.distinctValues(), Math.max(0, values.distinctValues() - shown),
                    values.tracesWithoutKey(), steps.followUp(), uiLink);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    @Tool(description = "Returns the individual traces carrying one attribute value, slowest first, with "
            + "the ids traces_traceExport takes and each trace's start as UTC epoch milliseconds: "
            + "'find the traces where tenant is acme and tell me why they were slow'. It returns "
            + "traces, not a comparison between populations - traces_attributeValues compares the "
            + "values. Paged: pass nextCursor back as cursor, with the same condition, while hasMore "
            + "is true. status NO_MATCH: no trace matched this condition.")
    @McpOutputSchema(Search.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.TRACES)
    public McpToolResult attributeSearch(
            @ToolParam(required = true, description = "The attribute key name, from traces_attributeKeys")
            String key,
            @ToolParam(required = false, description = "How to match: EQ (the default), NOT_EQ, CONTAINS, GT, GTE, LT, "
                    + "LTE, or EXISTS for 'carried the key at all'")
            TraceAttributeOperator operator,
            @ToolParam(required = false, description = "The value to match. Not needed for EXISTS.")
            String value,
            @ToolParam(required = false, description = "Key source as traces_attributeKeys reported it: ATTRIBUTE (the "
                    + "default), EVENT_FIELD, SPAN_SHAPE, NOTIFICATION_ATTRIBUTE, NOTIFICATION_SHAPE")
            TraceAttributeSource source,
            @ToolParam(required = false, description = "Key owner, required for an EVENT_FIELD")
            String owner,
            @ToolParam(required = false, description = "TRACE (the default) matches when any span of the trace satisfies "
                    + "the condition; SPAN requires one single span to satisfy it")
            TraceAttributeScope scope,
            @ToolParam(required = false, description = "Maximum number of traces to return (default "
                    + DEFAULT_LIMIT + ", maximum " + MAX_LIMIT + "); fewer when they do not fit the answer")
            @ToolParamBounds(defaultValue = DEFAULT_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "The nextCursor of the previous page, with the same condition; "
                    + "omit for the first page")
            String cursor) {

        TraceAttributeKeyId keyId = keyId(key, source, owner);
        TraceAttributeOperator matching = orDefault(operator, TraceAttributeOperator.EQ);
        TraceAttributeScope within = orDefault(scope, TraceAttributeScope.TRACE);
        String matched = blankToNull(value);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        McpCursor.Filters filters = McpCursor.Filters.of(FollowUpCalls.TRACES_ATTRIBUTE_SEARCH, profileId(),
                keyId.key(), keyId.source(), keyId.owner(), matching, matched, within);
        int start = OffsetPaging.offset(cursor, filters);
        String uiLink = UiLinks.view(profileId(), SEARCH_VIEW, searchQuery(keyId, matching, matched, within));

        TraceAttributeSearchQuery query = new TraceAttributeSearchQuery(
                List.of(new TraceAttributeCondition(keyId, matching, matched)),
                within,
                TraceSortField.DURATION,
                true,
                rows,
                start);

        TraceAttributeSearchResult result = profileManager.traceAttributesManager().search(query);
        if (result.matches().isEmpty() && result.totalMatching() == 0) {
            McpFollowUp followUp = NextSteps.builder(advertised)
                    .next(keyCall(FollowUpCalls.TRACES_ATTRIBUTE_VALUES, keyId.key(), keyId.source(), keyId.owner())
                            .why(VALUES_WHY))
                    .followUp();
            return McpToolResult.of(new Search(SearchStatus.NO_MATCH, NOTHING_MATCHED, profileId(), keyId.key(),
                    keyId.source(), keyId.owner(), matching, matched, within, List.of(), 0, false, null, null,
                    followUp, uiLink));
        }

        List<Match> all = result.matches().stream()
                .map(match -> Match.of(match, TraceLinks.trace(profileId(), match.trace().traceId())))
                .toList();
        // A total that drifted below the rows actually returned is raised to them.
        int total = Math.toIntExact(Math.max(result.totalMatching(), (long) start + all.size()));
        Search answer = FittingPage.largest(all.size(), shown -> {
            List<Match> matches = List.copyOf(all.subList(0, shown));
            McpCursor.Next next = OffsetPaging.next(filters, start, shown, start + shown < total);
            NextSteps.Builder steps = NextSteps.builder(advertised);
            if (!matches.isEmpty()) {
                steps.next(call(FollowUpCalls.TRACES_TRACE_EXPORT)
                        .with(FollowUpCalls.TRACE_ID, matches.getFirst().trace().traceId())
                        .why(EXPORT_WHY));
            }
            steps.nextWhen(next.hasMore(), keyCall(FollowUpCalls.TRACES_ATTRIBUTE_SEARCH,
                            keyId.key(), keyId.source(), keyId.owner())
                            .with(FollowUpCalls.OPERATOR, matching)
                            .with(FollowUpCalls.VALUE, matched)
                            .with(FollowUpCalls.SCOPE, within)
                            .with(FollowUpCalls.LIMIT, rows)
                            .with(FollowUpCalls.CURSOR, next.nextCursor())
                            .why(NEXT_PAGE_WHY))
                    .guidance(NOT_THE_CAUSE_GUIDANCE);
            return new Search(SearchStatus.OK, null, profileId(), keyId.key(), keyId.source(), keyId.owner(),
                    matching, matched, within, matches, total, next.hasMore(), next.nextCursor(), result.stats(),
                    steps.followUp(), uiLink);
        }, FittingPage::fits).orElseThrow(() -> new ToolExecutionException(ROW_TOO_LARGE));
        return McpToolResult.of(answer);
    }

    /**
     * The triple that identifies a key. Rejected by name rather than defaulted when the source is
     * unknown: guessing ATTRIBUTE for what was really an EVENT_FIELD silently returns nothing.
     */
    private static TraceAttributeKeyId keyId(String key, TraceAttributeSource source, String owner) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required; traces_attributeKeys lists them");
        }
        return new TraceAttributeKeyId(
                orDefault(source, TraceAttributeSource.ATTRIBUTE), blankToNull(owner), key.trim());
    }

    /**
     * The value an omitted optional argument stands for.
     * <p>
     * These used to be four parsers reading a string and refusing what they did not recognise. The
     * schema now carries the alternatives — a real {@code enum} parameter advertises its constants and
     * the binder refuses an unknown one by name — so all that is left of them is which constant the
     * argument means when it is not there at all.
     */
    private static <E extends Enum<E>> E orDefault(E value, E fallback) {
        return value == null ? fallback : value;
    }

    private McpNextTool.Call keyCall(String tool, String key, TraceAttributeSource source, String owner) {
        return call(tool)
                .with(FollowUpCalls.KEY, key)
                .with(FollowUpCalls.SOURCE, source)
                .with(FollowUpCalls.OWNER, owner);
    }

    private McpNextTool.Call call(String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, profileId());
    }

    /**
     * The search page opened on this very search: its one condition as the page encodes one -
     * {@code source~owner~key~operator~value}, the value left off for EXISTS, which takes none - and the
     * scope it matched within.
     */
    private static Map<String, String> searchQuery(
            TraceAttributeKeyId key, TraceAttributeOperator operator, String value, TraceAttributeScope scope) {

        List<String> fields = new ArrayList<>(List.of(
                key.source().name(), key.owner() == null ? "" : key.owner(), key.key(), operator.name()));
        if (operator != TraceAttributeOperator.EXISTS) {
            fields.add(value == null ? "" : value);
        }
        Map<String, String> query = UiLinks.query();
        query.put(WHERE_PARAM, String.join(CONDITION_SEPARATOR, fields));
        query.put(SCOPE_PARAM, scope.name());
        return query;
    }

    private static Map<String, String> keyQuery(TraceAttributeKeyId key, String eventType) {
        Map<String, String> query = UiLinks.query();
        query.put(KEY_PARAM, key.key());
        query.put(SOURCE_PARAM, key.source().name());
        query.put(OWNER_PARAM, key.owner());
        query.put(EVENT_TYPE_PARAM, eventType);
        return query;
    }


    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String profileId() {
        return profileManager.info().id();
    }
}
