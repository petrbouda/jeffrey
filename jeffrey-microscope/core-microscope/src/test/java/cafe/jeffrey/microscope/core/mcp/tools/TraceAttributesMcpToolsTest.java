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

import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceAttributesManager;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TraceAttributesMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String TENANT_KEY = "tenant";
    private static final String ACME = "acme";
    private static final String HTTP_EVENT_TYPE = "jeffrey.HttpServerExchange";
    private static final String TRACE_ID = "7f3a91";
    private static final String TOOL_PREFIX = "traces";
    private static final String SEARCH_TOOL = "traces_attributeSearch";
    private static final long TRACE_START_EPOCH_MS = 1_772_366_401_200L;

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 200;

    @Mock
    ProfileManager profileManager;

    @Mock
    TraceAttributesManager traceAttributesManager;

    /**
     * Every answer carries a link into the attribute views, which UiLinks builds off the request being
     * served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.traceAttributesManager()).thenReturn(traceAttributesManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private TraceAttributesMcpTools tools() {
        return new TraceAttributesMcpTools(profileManager, EVERY_FAMILY);
    }

    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(TraceAttributesMcpTools.class, method, result);
    }

    private static TraceAttributeKeyRow tenantKey() {
        return new TraceAttributeKeyRow(
                TraceAttributeSource.ATTRIBUTE.name(), null, TENANT_KEY, "STRING", 12, 900, 840, false);
    }

    private static TraceAttributeValues oneValue() {
        return new TraceAttributeValues(
                List.of(new TraceAttributeValues.Row(ACME, 120, 9_000_000, 40_000, 900_000, 2_100_000, 4)),
                18, 12, true);
    }

    private static TraceRow trace(String traceId) {
        return new TraceRow(
                traceId, "GET /orders", "SERVER", HTTP_EVENT_TYPE, 1_200, TRACE_START_EPOCH_MS, 4_000_000, 9, 0, true);
    }

    private static TraceAttributeSearchResult matches(int shown, long total) {
        TraceAttributeSearchResult.Hit hit = new TraceAttributeSearchResult.Hit(
                TraceAttributeCarrier.SPAN, "a1b2", TENANT_KEY, ACME);
        return new TraceAttributeSearchResult(
                IntStream.range(0, shown)
                        .mapToObj(index -> new TraceAttributeSearchResult.Match(
                                trace(index == 0 ? TRACE_ID : "7f3b" + index), List.of(hit)))
                        .toList(),
                total,
                new TraceAttributeSearchResult.Stats(total, 1, 9_000_000, 40_000, 900_000, 2_100_000));
    }

    private static TraceAttributeSearchResult oneMatch() {
        return matches(1, 7);
    }

    private TraceAttributeValueQuery capturedValueQuery() {
        ArgumentCaptor<TraceAttributeValueQuery> query =
                ArgumentCaptor.forClass(TraceAttributeValueQuery.class);
        verify(traceAttributesManager).values(query.capture());
        return query.getValue();
    }

    private TraceAttributeSearchQuery lastSearchQuery() {
        ArgumentCaptor<TraceAttributeSearchQuery> query =
                ArgumentCaptor.forClass(TraceAttributeSearchQuery.class);
        verify(traceAttributesManager, atLeastOnce()).search(query.capture());
        return query.getValue();
    }

    private JsonNode search(String value, String cursor) {
        return answer("attributeSearch", tools().attributeSearch(TENANT_KEY, null, value, null, null, null, 1, cursor));
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(TraceAttributesMcpTools.class));
    }

    @Nested
    class AttributeKeys {

        @Test
        void listsTheKeysWithTheTripleThatIdentifiesEachOne() {
            when(traceAttributesManager.keys()).thenReturn(List.of(tenantKey()));

            JsonNode out = answer("attributeKeys", tools().attributeKeys(null));
            JsonNode key = out.get("keys").get(0);

            assertEquals("OK", out.get("status").asString());
            assertEquals(TENANT_KEY, key.get("key").asString());
            assertEquals("ATTRIBUTE", key.get("source").asString());
            assertTrue(key.get("owner").isNull());
            assertEquals(12, key.get("distinctValues").asLong());
            assertTrue(out.get("uiLink").asString().contains("traces/attributes/search"));
        }

        @Test
        void routesToTheValuesOfTheFirstKeyWithItsWholeTriple() {
            when(traceAttributesManager.keys()).thenReturn(List.of(tenantKey()));

            JsonNode call = StructuredAnswers.call(answer("attributeKeys", tools().attributeKeys(null)),
                    "traces_attributeValues");

            assertEquals(TENANT_KEY, call.get("key").asString());
            assertEquals("ATTRIBUTE", call.get("source").asString());
            assertFalse(call.has("owner"), "a key without an owner is sent without one");
        }

        @Test
        void narrowsToOneEventTypeWhenOneIsNamed() {
            when(traceAttributesManager.keysOf(HTTP_EVENT_TYPE)).thenReturn(List.of(tenantKey()));

            JsonNode out = answer("attributeKeys", tools().attributeKeys("  " + HTTP_EVENT_TYPE + "  "));

            assertEquals(HTTP_EVENT_TYPE, out.get("eventType").asString());
            verify(traceAttributesManager).keysOf(HTTP_EVENT_TYPE);
        }

        /**
         * A recording can carry traces and no attributes at all, so the empty answer has to say which
         * of the two is missing and where the operation-level breakdown still lives.
         */
        @Test
        void saysWhichQuestionStillHasAnAnswerWhenNoKeyWasRecorded() {
            when(traceAttributesManager.keys()).thenReturn(List.of());

            JsonNode out = answer("attributeKeys", tools().attributeKeys(null));

            assertEquals("NO_ATTRIBUTES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no trace attributes"), out.toString());
            assertEquals(List.of("traces_operations"), StructuredAnswers.nextTools(out));
            assertEquals(0, out.get("keys").size());
        }
    }

    @Nested
    class AttributeValues {

        @Test
        void breaksTheKeyIntoValuesWithTheirOwnLatency() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            JsonNode out = answer("attributeValues", tools().attributeValues(TENANT_KEY, null, null, null, null, null));

            assertEquals(ACME, out.get("values").get(0).get("value").asString());
            assertEquals(900_000, out.get("values").get(0).get("p95Nanos").asLong());
            assertEquals(18, out.get("tracesWithoutKey").asLong());
            assertEquals(12, out.get("distinctValues").asLong());
            assertEquals(11, out.get("omittedValues").asInt(), "counted from the key's own cardinality");
            assertTrue(out.get("uiLink").asString().contains("traces/attributes/values"));
        }

        /** A value row comes from a NOT NULL column through an inner join, so the schema promises it. */
        @Test
        void aValueIsNeverNull() {
            assertEquals("string", StructuredAnswers.schemaTypeOf(
                    TraceAttributesMcpTools.class, "attributeValues", "values", "value").asString());
        }

        @Test
        void routesToTheTracesOfTheCostliestValue() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            JsonNode call = StructuredAnswers.call(
                    answer("attributeValues", tools().attributeValues(TENANT_KEY, null, null, null, null, null)),
                    "traces_attributeSearch");

            assertEquals(TENANT_KEY, call.get("key").asString());
            assertEquals(ACME, call.get("value").asString());
            assertEquals("EQ", call.get("operator").asString());
        }

        /**
         * Ranking by call count answers a different question: a busy value and an expensive value are
         * rarely the same one, and it is the expensive one the tool is being asked for.
         */
        @Test
        void ranksByTotalTimeWhenNoSortIsGiven() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            tools().attributeValues(TENANT_KEY, null, null, null, null, null);

            assertEquals(TraceAttributeValueSortField.TOTAL_TIME, capturedValueQuery().sort());
        }

        /**
         * Most keys are attributes a developer attached by hand, so that is what an omitted source
         * stands for - the other four are things a key row has to have reported.
         */
        @Test
        void readsAnOmittedSourceAsAPlainAttribute() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            JsonNode out = answer("attributeValues", tools().attributeValues(TENANT_KEY, null, null, null, null, null));

            assertEquals(TraceAttributeSource.ATTRIBUTE, capturedValueQuery().key().source());
            assertNull(capturedValueQuery().key().owner());
            assertEquals("ATTRIBUTE", out.get("source").asString());
        }

        @Test
        void pushesTheGivenSortAndEventTypeDown() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            tools().attributeValues(
                    TENANT_KEY, TraceAttributeSource.ATTRIBUTE, null, HTTP_EVENT_TYPE,
                    TraceAttributeValueSortField.P95, null);

            assertEquals(TraceAttributeValueSortField.P95, capturedValueQuery().sort());
            assertEquals(HTTP_EVENT_TYPE, capturedValueQuery().eventType());
        }

        @Test
        void boundsTheValueCountItAsksFor() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            tools().attributeValues(TENANT_KEY, null, null, null, null, 5_000);

            assertEquals(MAX_LIMIT, capturedValueQuery().limit());
        }

        @Test
        void appliesTheDefaultLimitWhenNoneIsGiven() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            tools().attributeValues(TENANT_KEY, null, null, null, null, null);

            assertEquals(DEFAULT_LIMIT, capturedValueQuery().limit());
        }

        @Test
        void namesTheKeyThatProducedNothingAndTheToolThatListsThem() {
            when(traceAttributesManager.values(any()))
                    .thenReturn(new TraceAttributeValues(List.of(), 0, 0, false));

            JsonNode out = answer("attributeValues", tools().attributeValues(TENANT_KEY, null, null, null, null, null));

            assertEquals("NO_VALUES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("'" + TENANT_KEY + "'"), out.toString());
            assertEquals(List.of("traces_attributeKeys"), StructuredAnswers.nextTools(out));
            assertTrue(out.get("omittedValues").isNull());
        }

        @Test
        void refusesAMissingKey() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> tools().attributeValues(null, null, null, null, null, null));

            assertTrue(thrown.getMessage().contains("key is required"), thrown.getMessage());
        }
    }

    @Nested
    class AttributeSearch {

        @Test
        void returnsTheMatchingTracesWithTheSpanThatCarriedTheValue() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = search(ACME, null);
            JsonNode match = out.get("matches").get(0);

            assertEquals("OK", out.get("status").asString());
            assertEquals(TRACE_ID, match.get("trace").get("traceId").asString());
            assertEquals(TRACE_START_EPOCH_MS, match.get("trace").get("startEpochMs").asLong());
            assertFalse(match.get("trace").has("startMillisFromBeginning"), "one instant, on the epoch clock");
            assertEquals("a1b2", match.get("hits").get(0).get("spanId").asString());
            assertEquals(7, out.get("totalMatching").asLong());
            assertTrue(out.get("hasMore").asBoolean());
            assertFalse(out.get("stats").isNull());
        }

        /** A notification that matched outside any span has no span id; the answer says so with null. */
        @Test
        void aNotificationHitOutsideAnySpanConformsWithANullSpan() {
            TraceAttributeSearchResult.Hit hit = new TraceAttributeSearchResult.Hit(
                    TraceAttributeCarrier.NOTIFICATION, null, TENANT_KEY, ACME);
            when(traceAttributesManager.search(any())).thenReturn(new TraceAttributeSearchResult(
                    List.of(new TraceAttributeSearchResult.Match(trace(TRACE_ID), List.of(hit))), 1,
                    new TraceAttributeSearchResult.Stats(1, 0, 0, 0, 0, 0)));

            JsonNode out = search(ACME, null).get("matches").get(0).get("hits").get(0);

            assertTrue(out.get("spanId").isNull(), out.toString());
            assertEquals("NOTIFICATION", out.get("carrier").asString());
        }

        /** A hit's key and value are NOT NULL columns joined by an inner join, so the schema promises them. */
        @Test
        void aHitsKeyAndValueAreNeverNull() {
            for (String component : List.of("key", "value")) {
                assertEquals("string", StructuredAnswers.schemaTypeOf(TraceAttributesMcpTools.class,
                        "attributeSearch", "matches", "hits", component).asString(), component);
            }
        }

        @Test
        void routesToTheFirstTraceAndToTheNextPage() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = search(ACME, null);

            assertEquals(TRACE_ID, StructuredAnswers.call(out, "traces_traceExport").get("traceId").asString());
            JsonNode next = StructuredAnswers.call(out, SEARCH_TOOL);
            assertEquals(out.get("nextCursor").asString(), next.get("cursor").asString());
            assertEquals(ACME, next.get("value").asString());
        }

        @Test
        void continuesFromTheCursorItHandedOut() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());
            String cursor = search(ACME, null).get("nextCursor").asString();

            search(ACME, cursor);

            assertEquals(1, lastSearchQuery().offset());
        }

        @Test
        void refusesACursorHandedOutForAnotherValue() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());
            String cursor = search(ACME, null).get("nextCursor").asString();

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> search("globex", cursor));

            assertTrue(thrown.getMessage().contains("other filters"), thrown.getMessage());
        }

        @Test
        void theLastPageHandsOutNoCursor() {
            when(traceAttributesManager.search(any())).thenReturn(matches(1, 1));

            JsonNode out = search(ACME, null);

            assertFalse(out.get("hasMore").asBoolean());
            assertTrue(out.get("nextCursor").isNull());
            assertFalse(StructuredAnswers.nextTools(out).contains(SEARCH_TOOL));
        }

        @Test
        void aTotalThatDriftedBelowTheRowsIsRaisedToThem() {
            TraceAttributeSearchResult drifted = oneMatch();
            when(traceAttributesManager.search(any())).thenReturn(new TraceAttributeSearchResult(
                    drifted.matches(), 0, drifted.stats()));

            JsonNode out = search(ACME, null);

            assertEquals(1, out.get("totalMatching").asLong());
            assertFalse(out.get("hasMore").asBoolean());
        }

        /** The page is what fits the answer: the cursor continues after the last match it actually carries. */
        @Test
        void fitsAPageOfManyMatchesAndContinuesAfterTheLastOneShown() {
            TraceAttributeSearchResult.Hit wide = new TraceAttributeSearchResult.Hit(
                    TraceAttributeCarrier.SPAN, "a1b2", TENANT_KEY, "x".repeat(4_000));
            List<TraceAttributeSearchResult.Match> rows = IntStream.range(0, MAX_LIMIT)
                    .mapToObj(index -> new TraceAttributeSearchResult.Match(trace("7f" + index), List.of(wide)))
                    .toList();
            when(traceAttributesManager.search(any())).thenReturn(new TraceAttributeSearchResult(
                    rows, 500, new TraceAttributeSearchResult.Stats(500, 0, 0, 0, 0, 0)));

            JsonNode out = answer("attributeSearch",
                    tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, MAX_LIMIT, null));
            int shown = out.get("matches").size();
            search(ACME, out.get("nextCursor").asString());

            assertTrue(shown < MAX_LIMIT, "shown " + shown);
            assertEquals(shown, lastSearchQuery().offset());
        }

        /**
         * The page reads its conditions from where= (source~owner~key~operator~value, the encoding of
         * the page's own encodeCondition) and its scope from scope=, so the link opens this very search.
         */
        @Test
        void linksTheSameConditionAndScope() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = search(ACME, null);

            String uiLink = out.get("uiLink").asString();
            assertTrue(uiLink.contains("where=ATTRIBUTE~~" + TENANT_KEY + "~EQ~" + ACME), uiLink);
            assertTrue(uiLink.contains("scope=TRACE"), uiLink);
            assertFalse(uiLink.contains("key="), uiLink);
            assertFalse(out.has("uiLinkNote"), "the link reproduces the search, so there is nothing to note");
        }

        /** EXISTS carries no value, and the page reads a trailing separator as an empty value. */
        @Test
        void anExistsConditionLinksWithoutAValue() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = answer("attributeSearch", tools().attributeSearch(TENANT_KEY,
                    TraceAttributeOperator.EXISTS, null, null, null, TraceAttributeScope.SPAN, 1, null));

            String uiLink = out.get("uiLink").asString();
            assertTrue(uiLink.contains("where=ATTRIBUTE~~" + TENANT_KEY + "~EXISTS&")
                    || uiLink.endsWith("where=ATTRIBUTE~~" + TENANT_KEY + "~EXISTS"), uiLink);
            assertTrue(uiLink.contains("scope=SPAN"), uiLink);
        }

        /** The owner travels in its slot, and a value keeps its own separators, encoded for the URL. */
        @Test
        void anOwnedKeyAndAValueWithSeparatorsTravelWhole() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = answer("attributeSearch", tools().attributeSearch(TENANT_KEY, null, "a~b c",
                    TraceAttributeSource.EVENT_FIELD, "jdbc", null, 1, null));

            String uiLink = out.get("uiLink").asString();
            assertTrue(uiLink.contains("where=EVENT_FIELD~jdbc~" + TENANT_KEY + "~EQ~a~b%20c"), uiLink);
        }

        /** The code already reads an omitted operator as EQ; the schema has to let a caller omit it. */
        @Test
        void theOperatorIsOptionalInTheSchema() throws NoSuchMethodException {
            Method search = TraceAttributesMcpTools.class.getMethod("attributeSearch",
                    String.class, TraceAttributeOperator.class, String.class, TraceAttributeSource.class,
                    String.class, TraceAttributeScope.class, Integer.class, String.class);

            assertFalse(search.getParameters()[1].getAnnotation(ToolParam.class).required());
        }

        /**
         * Attributes are per-carrier and never inherited down the tree, so the two scopes ask different
         * questions - and an omitted one has to stand for the wider of them rather than for whichever
         * the query builder finds cheaper.
         */
        @Test
        void matchesAnywhereInTheTraceWhenNoScopeIsGiven() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            JsonNode out = search(ACME, null);

            assertEquals(TraceAttributeScope.TRACE, lastSearchQuery().scope());
            assertEquals("TRACE", out.get("scope").asString());
        }

        @Test
        void comparesForEqualityWhenNoOperatorIsGiven() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            search(ACME, null);

            assertEquals(TraceAttributeOperator.EQ, lastSearchQuery().conditions().getFirst().operator());
        }

        @Test
        void pushesTheGivenOperatorAndScopeDown() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(
                    TENANT_KEY, TraceAttributeOperator.EXISTS, null, null, null,
                    TraceAttributeScope.SPAN, null, null);

            assertEquals(TraceAttributeOperator.EXISTS, lastSearchQuery().conditions().getFirst().operator());
            assertEquals(TraceAttributeScope.SPAN, lastSearchQuery().scope());
        }

        @Test
        void boundsTheTraceCountItAsksFor() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, 5_000, null);

            assertEquals(MAX_LIMIT, lastSearchQuery().limit());
        }

        /**
         * The key existing and the combination never occurring are different answers, and the second
         * one is what sends the reader to the value list rather than back to the key list.
         */
        @Test
        void separatesAnUnmatchedValueFromAnUnknownKey() {
            when(traceAttributesManager.search(any())).thenReturn(new TraceAttributeSearchResult(
                    List.of(), 0, new TraceAttributeSearchResult.Stats(0, 0, 0, 0, 0, 0)));

            JsonNode out = search("nobody", null);

            assertEquals("NO_MATCH", out.get("status").asString());
            assertTrue(out.get("reason").asString().startsWith("No trace matched this condition"), out.toString());
            assertFalse(out.get("reason").asString().contains("The key exists"),
                    "the key's existence was never checked, so the answer does not claim it");
            assertEquals(List.of("traces_attributeValues"), StructuredAnswers.nextTools(out));
            assertTrue(out.get("stats").isNull());
        }

        /**
         * An event field is qualified by the event type declaring it, so a search that omitted the
         * owner would silently be about a different key of the same name.
         */
        @Test
        void refusesAnEventFieldWithNoOwner() {
            assertThrows(IllegalArgumentException.class, () -> tools().attributeSearch(
                    TENANT_KEY, null, ACME, TraceAttributeSource.EVENT_FIELD, null, null, null, null));
        }

        /**
         * The refusal lives in the schema now that the operator is a real {@code enum}: the binder
         * knows the constants and names them, so the mistake can only be made through a call, which is
         * where the test makes it.
         */
        @Test
        void refusesAnUnknownOperatorByName() {
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), TOOL_PREFIX);

            ToolDispatchException thrown = assertThrows(ToolDispatchException.class, () -> toolset.call(
                    SEARCH_TOOL,
                    Json.createObject()
                            .put("key", TENANT_KEY)
                            .put("operator", "NONSENSE")
                            .put("value", ACME)));

            assertTrue(thrown.getMessage().contains("EQ"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("EXISTS"), thrown.getMessage());
        }
    }
}
