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
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
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

import java.util.List;
import java.util.Locale;
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

    private static final String SEARCH_VIEW = "traces/attributes/search";
    private static final String VALUES_VIEW = "traces/attributes/values";
    private static final String KEY_PARAM = "key";
    private static final String SOURCE_PARAM = "source";
    private static final String OWNER_PARAM = "owner";
    private static final String EVENT_TYPE_PARAM = "eventType";

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 200;

    private static final String NO_ATTRIBUTES =
            "This profile carries no trace attributes. They come from the Jeffrey tracing "
                    + "instrumentation recording them on a span; a recording with traces but no "
                    + "attributes has nothing to group by here. traces_operations still groups by "
                    + "operation.";

    private static final String NO_SUCH_KEY =
            "No attribute key '%s' was recorded. traces_attributeKeys lists the keys this profile "
                    + "holds, each with the source and owner that identify it.";

    private static final String NOTHING_MATCHED =
            "No trace matched. The key exists; this combination of operator and value did not occur. "
                    + "traces_attributeValues lists the values that were actually recorded for a key.";

    private static final String STEP_VALUES =
            "traces_attributeValues breaks one key into its values with a latency profile each, which "
                    + "is where a slow population separates from a healthy one.";
    private static final String STEP_SEARCH =
            "traces_attributeSearch finds the individual traces carrying one value, and their ids go "
                    + "to traces_traceExport.";
    private static final String STEP_EXPORT =
            "traces_traceExport opens one of these traces span by span; the ids above are what it takes.";
    private static final String STEP_NOT_THE_CAUSE =
            "An attribute says which population was slow, never why. The span tree of one of its "
                    + "traces says why.";

    private final ProfileManager profileManager;

    public TraceAttributesMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The attribute keys this recording's traces carried - tenant, customer, "
            + "feature flag, whatever the application recorded on its spans - each with how many "
            + "distinct values and traces it covers. Call this first: a key is identified by the "
            + "triple (source, owner, key), and the other tools in this family take all three.")
    public String attributeKeys(
            @ToolParam(description = "Optional event type to restrict the keys to, e.g. "
                    + "'jeffrey.HttpServerExchange'. Omit for every key in the profile.")
            String eventType) {

        List<TraceAttributeKeyRow> keys = eventType == null || eventType.isBlank()
                ? profileManager.traceAttributesManager().keys()
                : profileManager.traceAttributesManager().keysOf(eventType.trim());

        if (keys.isEmpty()) {
            return NO_ATTRIBUTES;
        }

        return LinkedOutput.json(new AttributeKeys(
                keys,
                NextSteps.builder().add(STEP_VALUES).add(STEP_SEARCH).build(),
                UiLinks.view(profileId(), SEARCH_VIEW)));
    }

    @Tool(description = "One attribute key broken into its values, each with how many traces carried "
            + "it and that value's own latency - total, p50, p95, max - and error count. This is the "
            + "tool for 'it is slow for one customer': a value whose p95 stands apart from the rest "
            + "names the population, which an operation-level percentile averages away. Trace counts "
            + "do not sum to the profile's total, because a trace whose spans recorded two values "
            + "counts under both.")
    public String attributeValues(
            @ToolParam(description = "The attribute key name, from traces_attributeKeys")
            String key,
            @ToolParam(description = "Where the key comes from, as traces_attributeKeys reported it: "
                    + "ATTRIBUTE (the default), EVENT_FIELD, SPAN_SHAPE, NOTIFICATION_ATTRIBUTE or "
                    + "NOTIFICATION_SHAPE")
            String source,
            @ToolParam(description = "The owner from traces_attributeKeys - for an EVENT_FIELD this is "
                    + "the event type declaring it, and it is required there. Omit when the key row "
                    + "showed none.")
            String owner,
            @ToolParam(description = "Optional event type to restrict to")
            String eventType,
            @ToolParam(description = "Order by one of: TOTAL_TIME (default), TRACES, P50, P95, MAX, "
                    + "ERRORS, VALUE")
            String sort,
            @ToolParam(description = "Maximum number of values to return (default 25, maximum 200)")
            Integer limit) {

        TraceAttributeValueQuery query = new TraceAttributeValueQuery(
                keyId(key, source, owner),
                valueSort(sort),
                true,
                boundedLimit(limit),
                blankToNull(eventType));

        TraceAttributeValues values = profileManager.traceAttributesManager().values(query);
        if (values.values().isEmpty()) {
            return NO_SUCH_KEY.formatted(key);
        }

        return LinkedOutput.json(new AttributeValues(
                key,
                values.values(),
                values.distinctValues(),
                values.tracesWithoutKey(),
                values.truncated(),
                NextSteps.builder().add(STEP_SEARCH).add(STEP_NOT_THE_CAUSE).build(),
                UiLinks.view(profileId(), VALUES_VIEW, keyQuery(key, source, owner, eventType))));
    }

    @Tool(description = "The individual traces carrying one attribute value, with their ids: 'find the "
            + "traces where tenant is acme and tell me why they were slow'. The ids go straight to "
            + "traces_traceExport. Use traces_attributeValues first to see which value is worth "
            + "chasing - this returns traces, not a comparison between populations.")
    public String attributeSearch(
            @ToolParam(description = "The attribute key name, from traces_attributeKeys")
            String key,
            @ToolParam(description = "How to match: EQ (the default), NOT_EQ, CONTAINS, GT, GTE, LT, "
                    + "LTE, or EXISTS for 'carried the key at all'")
            String operator,
            @ToolParam(description = "The value to match. Not needed for EXISTS.")
            String value,
            @ToolParam(description = "Key source as traces_attributeKeys reported it: ATTRIBUTE (the "
                    + "default), EVENT_FIELD, SPAN_SHAPE, NOTIFICATION_ATTRIBUTE, NOTIFICATION_SHAPE")
            String source,
            @ToolParam(description = "Key owner, required for an EVENT_FIELD")
            String owner,
            @ToolParam(description = "TRACE (the default) matches when any span of the trace satisfies "
                    + "the condition; SPAN requires one single span to satisfy it")
            String scope,
            @ToolParam(description = "Maximum number of traces to return (default 25, maximum 200)")
            Integer limit) {

        TraceAttributeCondition condition = new TraceAttributeCondition(
                keyId(key, source, owner), operator(operator), blankToNull(value));

        TraceAttributeSearchQuery query = new TraceAttributeSearchQuery(
                List.of(condition),
                scope(scope),
                TraceSortField.DURATION,
                true,
                boundedLimit(limit),
                0);

        TraceAttributeSearchResult result = profileManager.traceAttributesManager().search(query);
        if (result.matches().isEmpty()) {
            return NOTHING_MATCHED;
        }

        return LinkedOutput.json(new AttributeSearch(
                result.matches(),
                result.totalMatching(),
                NextSteps.builder().add(STEP_EXPORT).add(STEP_NOT_THE_CAUSE).build(),
                UiLinks.view(profileId(), SEARCH_VIEW, keyQuery(key, source, owner, null))));
    }

    /**
     * The triple that identifies a key. Rejected by name rather than defaulted when the source is
     * unknown: guessing ATTRIBUTE for what was really an EVENT_FIELD silently returns nothing.
     */
    private static TraceAttributeKeyId keyId(String key, String source, String owner) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required; traces_attributeKeys lists them");
        }
        return new TraceAttributeKeyId(source(source), blankToNull(owner), key.trim());
    }

    private static TraceAttributeSource source(String value) {
        if (value == null || value.isBlank()) {
            return TraceAttributeSource.ATTRIBUTE;
        }
        try {
            return TraceAttributeSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown attribute source '" + value
                    + "'. Valid sources: ATTRIBUTE, EVENT_FIELD, SPAN_SHAPE, NOTIFICATION_ATTRIBUTE, "
                    + "NOTIFICATION_SHAPE");
        }
    }

    private static TraceAttributeOperator operator(String value) {
        if (value == null || value.isBlank()) {
            return TraceAttributeOperator.EQ;
        }
        try {
            return TraceAttributeOperator.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown operator '" + value
                    + "'. Valid operators: EQ, NOT_EQ, CONTAINS, GT, GTE, LT, LTE, EXISTS");
        }
    }

    private static TraceAttributeScope scope(String value) {
        if (value == null || value.isBlank()) {
            return TraceAttributeScope.TRACE;
        }
        try {
            return TraceAttributeScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown scope '" + value + "'. Valid scopes: TRACE, SPAN");
        }
    }

    private static TraceAttributeValueSortField valueSort(String value) {
        if (value == null || value.isBlank()) {
            return TraceAttributeValueSortField.TOTAL_TIME;
        }
        try {
            return TraceAttributeValueSortField.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sort '" + value
                    + "'. Valid sorts: TOTAL_TIME, TRACES, P50, P95, MAX, ERRORS, VALUE");
        }
    }

    private static Map<String, String> keyQuery(
            String key, String source, String owner, String eventType) {

        Map<String, String> query = UiLinks.query();
        query.put(KEY_PARAM, key);
        query.put(SOURCE_PARAM, source);
        query.put(OWNER_PARAM, owner);
        query.put(EVENT_TYPE_PARAM, eventType);
        return query;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int boundedLimit(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record AttributeKeys(
            List<TraceAttributeKeyRow> keys, List<String> nextSteps, String uiLink) {
    }

    private record AttributeValues(
            String key,
            List<TraceAttributeValues.Row> values,
            long distinctValues,
            long tracesWithoutKey,
            boolean truncated,
            List<String> nextSteps,
            String uiLink) {
    }

    private record AttributeSearch(
            List<TraceAttributeSearchResult.Match> matches,
            long totalMatching,
            List<String> nextSteps,
            String uiLink) {
    }
}
