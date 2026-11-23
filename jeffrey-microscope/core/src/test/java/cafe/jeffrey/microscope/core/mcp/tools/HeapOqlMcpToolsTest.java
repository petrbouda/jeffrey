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

import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.mcp.server.McpNextToolConformance;
import cafe.jeffrey.microscope.mcp.server.ReflectiveToolset;
import cafe.jeffrey.microscope.core.utils.Json;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;
import cafe.jeffrey.microscope.common.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.microscope.core.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.microscope.core.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.microscope.core.profile.heapdump.model.OQLResumePoint;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpManager;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapOqlMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String QUERY = "SELECT * FROM INSTANCEOF java.util.HashMap";
    private static final String HASH_MAP = "java.util.HashMap";

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final Clock CLOCK = Clock.systemUTC();

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    /**
     * The answer carries a link into the OQL page, which UiLinks builds off the request being served.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "Profile", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HeapOqlMcpTools tools() {
        return new HeapOqlMcpTools(profileManager, EVERY_FAMILY);
    }

    private void answers(OQLQueryResult result) {
        when(heapDumpManager.executeQuery(any())).thenReturn(result);
    }

    private static OQLQueryResult twoInstances() {
        return OQLQueryResult.success(
                List.of(
                        OQLResultEntry.ofInstance(4711L, HASH_MAP, "{size=3}", 48),
                        OQLResultEntry.ofInstance(4712L, HASH_MAP, "{size=9}", 48)),
                2, false, 17);
    }

    private OQLQueryRequest capturedRequest() {
        ArgumentCaptor<OQLQueryRequest> request = ArgumentCaptor.forClass(OQLQueryRequest.class);
        verify(heapDumpManager).executeQuery(request.capture());
        return request.getValue();
    }

    /** The answer, checked against the schema the tool advertises, its link and its next calls. */
    private static JsonNode conforming(McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = result.structuredContent();
        McpSchemaConformance.assertConforms(structured, McpSchemaGenerator.schemaOf(outputSchema()));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        McpNextToolConformance.assertFollowable(structured, HeapDumpMcpToolsTest.reachable());
        return structured;
    }

    private static Class<? extends Record> outputSchema() {
        return Arrays.stream(HeapOqlMcpTools.class.getMethods())
                .filter(method -> method.getName().equals("oql"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(McpOutputSchema.class)
                .value();
    }

    private static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            if (call.get("tool").asString().equals(tool)) {
                return call.get("arguments");
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured);
    }

    @Nested
    class Oql {

        @Test
        void returnsTheRowsWithTheObjectIdsTheFollowUpToolsTakeAsStrings() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertEquals("OK", out.get("status").asString());
            assertEquals("4711", out.get("rows").get(0).get("objectId").asString());
            assertEquals(HASH_MAP, out.get("rows").get(0).get("className").asString());
            assertEquals(2, out.get("totalCount").asInt());
            assertEquals(17, out.get("executionTimeMs").asLong());
            assertEquals("4711", call(out, "heap_getPathToGCRoot").get("objectId").asString());
            assertEquals("4711", call(out, "heap_getInstanceDetail").get("objectId").asString());
        }

        /**
         * A row that is a computed value rather than an object has nothing to inspect further, and the
         * null objectId is what says so - dropping the field would read as an object without an id.
         */
        @Test
        void keepsTheNullObjectIdOfAComputedRow() {
            answers(OQLQueryResult.success(List.of(OQLResultEntry.ofValue("42")), 1, false, 3));

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertTrue(out.get("rows").get(0).get("objectId").isNull(), out.toString());
        }

        /** The OQL page does not take a query from its URL, so the link opens it and says so. */
        @Test
        void linksTheOqlPageAndSaysTheQueryIsNotCarried() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertTrue(out.get("uiLink").asString().endsWith("/profiles/" + PROFILE_ID + "/heap-dump/oql"),
                    out.toString());
            assertTrue(out.get("uiLinkNote").asString().contains("query"), out.toString());
        }

        /**
         * The engine reports a parse failure in the result rather than by throwing, and the message
         * names the position - which is what lets the model correct its own query rather than retry it.
         */
        @Test
        void handsBackTheEnginesOwnParseFailure() {
            answers(OQLQueryResult.error("Unexpected token at position 14", 2));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> complete(tools().oql("SELECT * FRM x", null, null, null, RESOURCE_READ)));

            assertTrue(error.getMessage().contains("position 14"), error.getMessage());
            assertFalse(error.getMessage().contains("\"rows\""), error.getMessage());
        }

        /** A very long value is cut, and a page that would still outgrow the answer ends early. */
        @Test
        void fitsAPageOfLongRowsAndContinuesAfterTheLastOneShown() {
            String longName = "com.acme." + "X".repeat(3_000);
            List<OQLResultEntry> rows = IntStream.range(0, MAX_LIMIT)
                    .mapToObj(i -> OQLResultEntry.ofInstance(i, longName, "v".repeat(5_000), 48))
                    .toList();
            answers(OQLQueryResult.success(rows, 500, true, 5));

            JsonNode out = conforming(tools().oql(QUERY, MAX_LIMIT, null, null, RESOURCE_READ));

            int shown = out.get("rows").size();
            assertTrue(shown > 0 && shown < MAX_LIMIT, "rows shown: " + shown);
            assertTrue(out.get("hasMore").asBoolean());
            assertTrue(out.get("rows").get(0).get("value").asString().length() < 5_000);

            complete(tools().oql(QUERY, MAX_LIMIT, out.get("nextCursor").asString(), null, RESOURCE_READ));
            ArgumentCaptor<OQLQueryRequest> requests = ArgumentCaptor.forClass(OQLQueryRequest.class);
            verify(heapDumpManager, times(2)).executeQuery(requests.capture());
            assertEquals(shown, requests.getAllValues().get(1).offset());
        }
    }

    /**
     * A page hands back the cursor for the next one, bound to the query it was returned for.
     */
    @Nested
    class Paging {

        @Test
        void aCursorReadsTheNextPage() {
            answers(OQLQueryResult.success(twoInstances().results(), 120, true, 5));

            JsonNode first = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ));
            String cursor = first.get("nextCursor").asString();
            complete(tools().oql(QUERY, 2, cursor, null, RESOURCE_READ));

            ArgumentCaptor<OQLQueryRequest> requests = ArgumentCaptor.forClass(OQLQueryRequest.class);
            verify(heapDumpManager, times(2)).executeQuery(requests.capture());
            assertEquals(2, requests.getAllValues().get(1).offset());
            assertEquals(cursor, call(first, "heap_oql").get("cursor").asString());
            assertEquals(QUERY, call(first, "heap_oql").get("query").asString());
        }

        /** The engine counts only the rows it handed back: while more remain, the query's own count is unknown. */
        @Test
        void aPageWithMoreAfterItLeavesTheCountUnknown() {
            answers(OQLQueryResult.success(twoInstances().results(), 2, true, 5));

            JsonNode out = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ));

            assertTrue(out.get("totalCount").isNull(), out::toString);
        }

        /** The page that ends the rows knows them all: every page before it and its own. */
        @Test
        void theLastPageCountsEveryRowOfTheQuery() {
            answers(OQLQueryResult.success(twoInstances().results(), 2, true, 5));
            String cursor = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ)).get("nextCursor").asString();
            answers(OQLQueryResult.success(List.of(twoInstances().results().getFirst()), 1, false, 5));

            JsonNode last = conforming(tools().oql(QUERY, 2, cursor, null, RESOURCE_READ));

            assertEquals(3, last.get("totalCount").asInt(), last::toString);
        }

        /**
         * A query the engine resumes continues from the point it handed back, not an offset — and the
         * last page still counts every row the pages before it showed.
         */
        @Test
        void aResumedQueryContinuesFromThePointTheEngineHandedBack() {
            OQLResumePoint point = new OQLResumePoint(12, 50, 3);
            answers(OQLQueryResult.success(twoInstances().results(), 2, true, 5, point));
            String cursor = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ)).get("nextCursor").asString();
            answers(OQLQueryResult.success(List.of(twoInstances().results().getFirst()), 1, false, 5));

            JsonNode last = conforming(tools().oql(QUERY, 2, cursor, null, RESOURCE_READ));

            ArgumentCaptor<OQLQueryRequest> requests = ArgumentCaptor.forClass(OQLQueryRequest.class);
            verify(heapDumpManager, times(2)).executeQuery(requests.capture());
            assertEquals(point, requests.getAllValues().get(1).resumeAfter());
            assertEquals(0, requests.getAllValues().get(1).offset());
            assertEquals(3, last.get("totalCount").asInt(), last::toString);
        }

        /**
         * A resumed page too large for one answer continues from its own start, passing over the rows
         * it showed: the engine's point lies past rows the reader never saw.
         */
        @Test
        void aResumedPageCutForItsSizeContinuesFromItsOwnStart() {
            OQLResumePoint start = new OQLResumePoint(12, 50, 3);
            answers(OQLQueryResult.success(twoInstances().results(), 2, true, 5, start));
            String cursor = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ)).get("nextCursor").asString();
            List<OQLResultEntry> large = IntStream.range(0, MAX_LIMIT)
                    .mapToObj(i -> OQLResultEntry.ofInstance(i, "com.acme." + "X".repeat(3_000), "v".repeat(5_000), 48))
                    .toList();
            answers(OQLQueryResult.success(large, MAX_LIMIT, true, 5, new OQLResumePoint(12, 90, 1)));

            JsonNode cut = conforming(tools().oql(QUERY, MAX_LIMIT, cursor, null, RESOURCE_READ));
            int shown = cut.get("rows").size();
            complete(tools().oql(QUERY, MAX_LIMIT, cut.get("nextCursor").asString(), null, RESOURCE_READ));

            assertTrue(shown > 0 && shown < MAX_LIMIT, "rows shown: " + shown);
            ArgumentCaptor<OQLQueryRequest> requests = ArgumentCaptor.forClass(OQLQueryRequest.class);
            verify(heapDumpManager, times(3)).executeQuery(requests.capture());
            assertEquals(start.passingOver(shown), requests.getAllValues().get(2).resumeAfter());
        }

        @Test
        void aMalformedResumeCursorIsRefused() {
            OQLResumePoint point = new OQLResumePoint(12, 50, 3);
            answers(OQLQueryResult.success(twoInstances().results(), 2, true, 5, point));
            String cursor = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ)).get("nextCursor").asString();

            assertThrows(IllegalArgumentException.class,
                    () -> tools().oql(QUERY, 2, cursor.substring(0, cursor.length() - 2), null, RESOURCE_READ));
        }

        @Test
        void saysNothingOfANextPageOnTheLastOne() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertFalse(out.get("hasMore").asBoolean());
            assertTrue(out.get("nextCursor").isNull());
        }

        @Test
        void aCursorOfAnotherQueryIsRefused() {
            answers(OQLQueryResult.success(twoInstances().results(), 120, true, 5));
            String cursor = conforming(tools().oql(QUERY, 2, null, null, RESOURCE_READ)).get("nextCursor").asString();

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> tools().oql("SELECT * FROM java.lang.String", 2, cursor, null, RESOURCE_READ));

            assertTrue(error.getMessage().contains("other filters"), error.getMessage());
        }

        /**
         * A query that matches nothing is an answer, not a failure: a zero-row result the reader can
         * read the query's own verdict from.
         */
        @Test
        void answersAQueryThatMatchesNothingWithZeroRows() {
            answers(OQLQueryResult.success(List.of(), 0, false, 1));

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertEquals(0, out.get("rows").size());
            assertEquals(0, out.get("totalCount").asInt());
        }

        @Test
        void takesNoOffsetAnyMore() {
            McpToolSpec spec = new ReflectiveToolset(tools(), "heap").specs().getFirst();

            assertFalse(spec.inputSchema().path("properties").has("offset"));
            assertTrue(spec.inputSchema().path("properties").has("cursor"));
        }

        /** One clamp convention across every tool: zero or below is the default, not one row. */
        @Test
        void readsANonPositiveLimitAsTheDefault() {
            answers(twoInstances());

            complete(tools().oql(QUERY, 0, null, null, RESOURCE_READ));

            assertEquals(DEFAULT_LIMIT, capturedRequest().limit());
        }

        @Test
        void capsALimitAboveTheMaximum() {
            answers(twoInstances());

            complete(tools().oql(QUERY, 5_000, null, null, RESOURCE_READ));

            assertEquals(MAX_LIMIT, capturedRequest().limit());
        }
    }

    /**
     * Retained sizes build the dominator tree first, which on a large dump takes minutes — so that
     * path runs the way the writers do: waited on, then handed back as an operation to poll.
     */
    @Nested
    class RetainedSize {

        /** With includeRetainedSize the rows come back on the same call. */
        @Test
        void answersTheRowsSynchronously() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, true, RESOURCE_READ));

            assertEquals("4711", out.get("rows").get(0).get("objectId").asString());
        }

        /** A failed query is raised as a tool error carrying the parser's message. */
        @Test
        void aFailedRunIsAnError() {
            answers(OQLQueryResult.error("Unexpected token at position 14", 2));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> complete(tools().oql("SELECT * FRM x", 7, null, true, RESOURCE_READ)));

            assertTrue(error.getMessage().contains("position 14"), error.getMessage());
        }

        @Test
        void aPlainQueryAnswersTheRowsDirectly() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertEquals("4711", out.get("rows").get(0).get("objectId").asString());
        }

        @Test
        void declaresItselfAWriterThatIsSafeToRepeat() {
            McpToolSpec spec = new ReflectiveToolset(tools(), "heap").specs().stream()
                    .filter(candidate -> candidate.name().equals("heap_oql"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(spec.annotations().readOnly());
            assertTrue(spec.annotations().idempotent());
        }
    }

    @Nested
    class Arguments {

        @Test
        void refusesAMissingQueryWithoutTouchingTheDump() {
            IllegalArgumentException thrown = assertThrows(
                    IllegalArgumentException.class, () -> complete(tools().oql(null, null, null, null, RESOURCE_READ)));

            assertTrue(thrown.getMessage().contains("query is required"), thrown.getMessage());
            verify(heapDumpManager, never()).executeQuery(any());
        }

        @Test
        void refusesABlankQueryTheSameWay() {
            assertThrows(IllegalArgumentException.class,
                    () -> complete(tools().oql("   ", null, null, null, RESOURCE_READ)));
        }

        @Test
        void trimsTheQueryBeforeRunningIt() {
            answers(twoInstances());

            complete(tools().oql("  " + QUERY + "  ", null, null, null, RESOURCE_READ));

            assertEquals(QUERY, capturedRequest().query());
        }

        @Test
        void startsAtTheFirstRowWithoutACursor() {
            answers(twoInstances());

            complete(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertEquals(0, capturedRequest().offset());
            assertEquals(DEFAULT_LIMIT, capturedRequest().limit());
        }

        /**
         * Retained size builds the dominator tree first, which on a large dump takes minutes, so it is
         * opted into rather than paid for by accident.
         */
        @Test
        void leavesRetainedSizeOffUnlessItWasAskedFor() {
            answers(twoInstances());

            complete(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertFalse(capturedRequest().includeRetainedSize());
        }

        @Test
        void computesRetainedSizeWhenItWasAskedFor() {
            answers(twoInstances());

            complete(tools().oql(QUERY, null, null, true, RESOURCE_READ));

            assertTrue(capturedRequest().includeRetainedSize());
        }
    }
}
