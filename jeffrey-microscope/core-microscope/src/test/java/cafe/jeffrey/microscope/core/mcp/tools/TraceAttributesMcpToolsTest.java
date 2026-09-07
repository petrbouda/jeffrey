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

import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceAttributesManager;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.ToolDispatchException;
import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        return new TraceAttributesMcpTools(profileManager);
    }

    private static TraceAttributeKeyRow tenantKey() {
        return new TraceAttributeKeyRow(
                TraceAttributeSource.ATTRIBUTE.name(), null, TENANT_KEY, "STRING", 12, 900, 840, false);
    }

    private static TraceAttributeValues oneValue() {
        return new TraceAttributeValues(
                List.of(new TraceAttributeValues.Row(ACME, 120, 9_000_000, 40_000, 900_000, 2_100_000, 4)),
                18, 12, false);
    }

    private static TraceAttributeSearchResult oneMatch() {
        TraceRow trace = new TraceRow(
                TRACE_ID, "GET /orders", "SERVER", HTTP_EVENT_TYPE, 1_200, 1_200, 4_000_000, 9, 0, true);
        TraceAttributeSearchResult.Hit hit = new TraceAttributeSearchResult.Hit(
                TraceAttributeCarrier.SPAN, "a1b2", TENANT_KEY, ACME);
        return new TraceAttributeSearchResult(
                List.of(new TraceAttributeSearchResult.Match(trace, List.of(hit))),
                7,
                new TraceAttributeSearchResult.Stats(7, 1, 9_000_000, 40_000, 900_000, 2_100_000));
    }

    private TraceAttributeValueQuery capturedValueQuery() {
        ArgumentCaptor<TraceAttributeValueQuery> query =
                ArgumentCaptor.forClass(TraceAttributeValueQuery.class);
        verify(traceAttributesManager).values(query.capture());
        return query.getValue();
    }

    private TraceAttributeSearchQuery capturedSearchQuery() {
        ArgumentCaptor<TraceAttributeSearchQuery> query =
                ArgumentCaptor.forClass(TraceAttributeSearchQuery.class);
        verify(traceAttributesManager).search(query.capture());
        return query.getValue();
    }

    @Nested
    class AttributeKeys {

        @Test
        void listsTheKeysWithTheTripleThatIdentifiesEachOne() {
            when(traceAttributesManager.keys()).thenReturn(List.of(tenantKey()));

            String out = tools().attributeKeys(null);

            assertTrue(out.contains("\"key\":\"" + TENANT_KEY + "\""), out);
            assertTrue(out.contains("\"source\":\"ATTRIBUTE\""), out);
            assertTrue(out.contains("\"distinctValues\":12"), out);
            assertTrue(out.contains("traces/attributes/search"), out);
        }

        @Test
        void narrowsToOneEventTypeWhenOneIsNamed() {
            when(traceAttributesManager.keysOf(HTTP_EVENT_TYPE)).thenReturn(List.of(tenantKey()));

            String out = tools().attributeKeys("  " + HTTP_EVENT_TYPE + "  ");

            assertTrue(out.contains(TENANT_KEY), out);
            verify(traceAttributesManager).keysOf(HTTP_EVENT_TYPE);
        }

        /**
         * A recording can carry traces and no attributes at all, so the empty answer has to say which
         * of the two is missing and where the operation-level breakdown still lives.
         */
        @Test
        void saysWhichQuestionStillHasAnAnswerWhenNoKeyWasRecorded() {
            when(traceAttributesManager.keys()).thenReturn(List.of());

            String out = tools().attributeKeys(null);

            assertTrue(out.contains("no trace attributes"), out);
            assertTrue(out.contains("traces_operations"), out);
            assertFalse(out.contains("\"keys\""), out);
        }
    }

    @Nested
    class AttributeValues {

        @Test
        void breaksTheKeyIntoValuesWithTheirOwnLatency() {
            when(traceAttributesManager.values(any())).thenReturn(oneValue());

            String out = tools().attributeValues(TENANT_KEY, null, null, null, null, null);

            assertTrue(out.contains("\"value\":\"" + ACME + "\""), out);
            assertTrue(out.contains("\"p95Nanos\":900000"), out);
            assertTrue(out.contains("\"tracesWithoutKey\":18"), out);
            assertTrue(out.contains("traces/attributes/values"), out);
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

            tools().attributeValues(TENANT_KEY, null, null, null, null, null);

            assertEquals(TraceAttributeSource.ATTRIBUTE, capturedValueQuery().key().source());
            assertNull(capturedValueQuery().key().owner());
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

            String out = tools().attributeValues(TENANT_KEY, null, null, null, null, null);

            assertTrue(out.contains("'" + TENANT_KEY + "'"), out);
            assertTrue(out.contains("traces_attributeKeys"), out);
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

            String out = tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, null);

            assertTrue(out.contains("\"traceId\":\"" + TRACE_ID + "\""), out);
            assertTrue(out.contains("\"spanId\":\"a1b2\""), out);
            assertTrue(out.contains("\"totalMatching\":7"), out);
        }

        /**
         * Attributes are per-carrier and never inherited down the tree, so the two scopes ask different
         * questions - and an omitted one has to stand for the wider of them rather than for whichever
         * the query builder finds cheaper.
         */
        @Test
        void matchesAnywhereInTheTraceWhenNoScopeIsGiven() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, null);

            assertEquals(TraceAttributeScope.TRACE, capturedSearchQuery().scope());
        }

        @Test
        void comparesForEqualityWhenNoOperatorIsGiven() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, null);

            assertEquals(TraceAttributeOperator.EQ,
                    capturedSearchQuery().conditions().getFirst().operator());
        }

        @Test
        void pushesTheGivenOperatorAndScopeDown() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(
                    TENANT_KEY, TraceAttributeOperator.EXISTS, null, null, null,
                    TraceAttributeScope.SPAN, null);

            assertEquals(TraceAttributeOperator.EXISTS,
                    capturedSearchQuery().conditions().getFirst().operator());
            assertEquals(TraceAttributeScope.SPAN, capturedSearchQuery().scope());
        }

        @Test
        void boundsTheTraceCountItAsksFor() {
            when(traceAttributesManager.search(any())).thenReturn(oneMatch());

            tools().attributeSearch(TENANT_KEY, null, ACME, null, null, null, 5_000);

            assertEquals(MAX_LIMIT, capturedSearchQuery().limit());
        }

        /**
         * The key existing and the combination never occurring are different answers, and the second
         * one is what sends the reader to the value list rather than back to the key list.
         */
        @Test
        void separatesAnUnmatchedValueFromAnUnknownKey() {
            when(traceAttributesManager.search(any())).thenReturn(new TraceAttributeSearchResult(
                    List.of(), 0, new TraceAttributeSearchResult.Stats(0, 0, 0, 0, 0, 0)));

            String out = tools().attributeSearch(TENANT_KEY, null, "nobody", null, null, null, null);

            assertTrue(out.contains("The key exists"), out);
            assertTrue(out.contains("traces_attributeValues"), out);
        }

        /**
         * An event field is qualified by the event type declaring it, so a search that omitted the
         * owner would silently be about a different key of the same name.
         */
        @Test
        void refusesAnEventFieldWithNoOwner() {
            assertThrows(IllegalArgumentException.class, () -> tools().attributeSearch(
                    TENANT_KEY, null, ACME, TraceAttributeSource.EVENT_FIELD, null, null, null));
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
