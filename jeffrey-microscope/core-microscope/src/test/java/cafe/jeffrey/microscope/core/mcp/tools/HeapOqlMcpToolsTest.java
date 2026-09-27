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
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.heapdump.model.OQLQueryRequest;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
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
    private static final String OPERATION_PREFIX = "Operation: ";

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
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);

    private final AtomicInteger leases = new AtomicInteger();

    private Duration waitBudget = Duration.ofSeconds(5);

    private OperationAnswers answers = ToolFixtures.answers();

    private HeapOqlMcpTools tools() {
        BoundedOperation<OQLQueryResult> retained = new BoundedOperation<>(
                OperationKind.HEAP_OQL,
                ToolFixtures.jobs(waitBudget, BoundedJobs.COMPLETED_RETENTION, CLOCK),
                operations, answers);
        return new HeapOqlMcpTools(profileManager, retained, () -> {
            leases.incrementAndGet();
            return leases::decrementAndGet;
        }, EVERY_FAMILY);
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

        /** The rows are the answer's own; the operation beside them does not repeat them. */
        @Test
        void answersWithinTheBudgetCarryingTheOperationWithoutRepeatingTheRows() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, true, RESOURCE_READ));

            assertEquals("4711", out.get("rows").get(0).get("objectId").asString());
            String operationId = out.get("operationId").asString();
            assertEquals(operationId, out.get("operation").get("operationId").asString());
            assertTrue(out.get("operation").get("result").isNull(), out.toString());
            assertTrue(Json.toString(operations.status(operationId)).contains("\"4711\""),
                    "operations_status still carries the rows");
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        }

        @Test
        void handsBackARunningStatusWithTheOperationWhenTheQueryOutlastsTheBudget() throws Exception {
            waitBudget = Duration.ofMillis(50);
            CountDownLatch release = new CountDownLatch(1);
            when(heapDumpManager.executeQuery(any())).thenAnswer(invocation -> {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return twoInstances();
            });

            JsonNode started = conforming(tools().oql(QUERY, null, null, true, RESOURCE_READ));
            String operationId = started.path("operationId").asString();

            assertEquals("RUNNING", started.path("status").asString(), started.toString());
            assertFalse(started.path("followUp").path("guidance").toString()
                    .contains(started.path("reason").asString()), "the reason is not repeated as guidance");
            assertEquals("HEAP_OQL", started.path("operation").path("kind").asString());
            assertEquals(0, started.get("rows").size());
            assertEquals(operationId, call(started, "operations_status").get("operationId").asString());
            assertEquals(1, leases.get(), "the worker holds the profile open while the query runs");

            release.countDown();
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> operations.status(operationId).status() == OperationState.COMPLETED);
            String polled = Json.toString(operations.status(operationId));
            assertTrue(polled.contains("\"4711\""), polled);
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        }

        /**
         * A failed run's operation names the call that starts it again, with its arguments, rather
         * than prose about retrying.
         */
        @Test
        void aFailedRunNamesTheRetryCall() {
            answers(OQLQueryResult.error("Unexpected token at position 14", 2));

            ToolExecutionException error = assertThrows(
                    ToolExecutionException.class,
                    () -> complete(tools().oql("SELECT * FRM x", 7, null, true, RESOURCE_READ)));

            assertTrue(error.getMessage().contains("position 14"), error.getMessage());
            JsonNode operation = Json.readTree(error.getMessage().substring(
                    error.getMessage().indexOf(OPERATION_PREFIX) + OPERATION_PREFIX.length()));
            JsonNode retry = operation.get("followUp").get("nextTools").get(0);
            assertEquals("heap_oql", retry.get("tool").asString());
            assertEquals("SELECT * FRM x", retry.get("arguments").get("query").asString());
            assertTrue(retry.get("arguments").get("includeRetainedSize").asBoolean());
            assertEquals(PROFILE_ID, retry.get("arguments").get("profileId").asString());
            assertEquals(1, McpNextToolConformance.assertFollowable(operation, HeapDumpMcpToolsTest.reachable()));
        }

        @Test
        void aPlainQueryNeverBecomesAnOperation() {
            answers(twoInstances());

            JsonNode out = conforming(tools().oql(QUERY, null, null, null, RESOURCE_READ));

            assertTrue(out.get("operationId").isNull(), out.toString());
            assertTrue(out.get("operation").isNull(), out.toString());
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

    /**
     * A client that declared the tasks extension gets a query that builds the dominator tree handed
     * back as a task after the task budget rather than the standard 45 s, and the task answers with
     * the rows.
     */
    @Nested
    class TaskCapableClient {

        @Test
        void handsBackTheRunningQueryAsATaskWellBeforeTheStandardWait() throws Exception {
            waitBudget = BoundedJobs.WAIT_BUDGET;
            answers = new OperationAnswers(SHORT_TASK_WAIT);
            CountDownLatch release = new CountDownLatch(1);
            when(heapDumpManager.executeQuery(any())).thenAnswer(invocation -> {
                assertTrue(release.await(60, TimeUnit.SECONDS));
                return twoInstances();
            });
            HeapOqlMcpTools tools = tools();

            McpToolOutcome outcome;
            try {
                outcome = assertTimeout(Duration.ofSeconds(20),
                        () -> tools.oql(QUERY, null, null, true, TASKS));
            } catch (AssertionError e) {
                release.countDown();
                throw e;
            }

            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
            assertEquals(OperationKind.HEAP_OQL, operations.status(taskId).kind());
            assertEquals(McpTaskStatus.WORKING, operations.task(taskId, kind -> true).status());

            release.countDown();
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                    McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
            McpToolResult answer = assertInstanceOf(McpTaskState.Completed.class,
                    operations.task(taskId, kind -> true).state()).result();
            JsonNode structured = conforming(answer);
            assertEquals("4711", structured.get("rows").get(0).get("objectId").asString());
            assertEquals(taskId, structured.path("operationId").asString());
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
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
