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

package cafe.jeffrey.flamegraph.code;

import cafe.jeffrey.flamegraph.code.CallSites.CallSite;
import cafe.jeffrey.jfrparser.api.type.JfrClass;
import cafe.jeffrey.jfrparser.api.type.JfrMethod;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.provider.profile.api.CallSiteRecord;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallSiteBuilderTest {

    private static final String JIT = "JIT compiled";
    private static final FrameScope OWN = new FrameScope.OwnCode(List.of("com.acme."));
    private static final String FIND_ORDER = "SELECT orders";
    private static final String FIND_LINES = "SELECT order_lines";
    private static final TraceOperationId SHOW_ORDER = new TraceOperationId("GET /orders/{id}", "SERVER",
            "jeffrey.HttpServerExchange");

    private static final JfrStackFrame RUN = frame("java.lang.Thread", "run", 0);
    private static final JfrStackFrame HANDLER = frame("com.acme.web.OrderController", "show", 40);
    private static final JfrStackFrame FIND = frame("com.acme.orders.OrderRepository", "findById", 111);
    private static final JfrStackFrame LINES_LOOP = frame("com.acme.orders.OrderRepository", "linesOf", 140);
    private static final JfrStackFrame EMIT = frame("cafe.jeffrey.jfr.events.TracedEvents", "emit", 119);
    private static final JfrStackFrame COMMIT = frame("cafe.jeffrey.jfr.events.trace.AbstractTracedEvent", "commitSpan", 157);

    private record TestClass(String className, String hiddenClassId) implements JfrClass {
    }

    private record TestMethod(JfrClass clazz, String methodName) implements JfrMethod {
    }

    private record TestFrame(String type, int lineNumber, JfrMethod method) implements JfrStackFrame {

        @Override
        public int bytecodeIndex() {
            return -1;
        }
    }

    private record TestStackTrace(long id, List<? extends JfrStackFrame> frames) implements JfrStackTrace {
    }

    private static JfrStackFrame frame(String className, String methodName, int line) {
        return new TestFrame(JIT, line, new TestMethod(new TestClass(className, null), methodName));
    }

    private static CallSiteRecord row(String group, Long trace, long count, long nanos, JfrStackFrame... rootFirst) {
        return new CallSiteRecord(new TestStackTrace(1, Arrays.asList(rootFirst)), group,
                group == null ? null : group.length(), group, trace,
                trace == null ? null : SHOW_ORDER, count, nanos, nanos / Math.max(1, count));
    }

    private static CallSites read(FrameScope scope, CallSiteRecord... rows) {
        CallSiteBuilder builder = new CallSiteBuilder(scope);
        for (CallSiteRecord row : rows) {
            builder.onRecord(row);
        }
        return builder.build();
    }

    @Nested
    class Attribution {

        @Test
        void aStatementIsChargedToTheOwnLineThatIssuedItBelowTheTracingFrames() {
            CallSites sites = read(OWN, row(FIND_ORDER, null, 10, 5_000, RUN, HANDLER, FIND, EMIT, COMMIT));

            CallSite site = sites.sites().getFirst();
            assertEquals(new MethodKey("com.acme.orders.OrderRepository", "findById"), site.method());
            assertEquals(111, site.line());
            assertEquals(List.of(
                    new CallSites.PathFrame(new MethodKey("com.acme.orders.OrderRepository", "findById"), 111),
                    new CallSites.PathFrame(new MethodKey("com.acme.web.OrderController", "show"), 40)),
                    site.ownPath());
            assertEquals(FIND_ORDER, site.groupValue());
            assertEquals(10, site.count());
            assertEquals(5_000, site.totalNanos());
        }

        @Test
        void anOwnWrapperCalledFromTwoPlacesIsTwoSites() {
            JfrStackFrame wrapper = frame("com.acme.db.DatabaseClient", "query", 170);
            JfrStackFrame other = frame("com.acme.orders.InvoiceRepository", "findAll", 55);
            CallSites sites = read(OWN,
                    row(FIND_ORDER, null, 3, 300, RUN, FIND, wrapper, EMIT),
                    row(FIND_ORDER, null, 1, 100, RUN, other, wrapper, EMIT));

            assertEquals(2, sites.sites().size());
            assertTrue(sites.sites().stream().allMatch(site -> site.method().methodName().equals("query")));
            assertEquals(List.of(3L, 1L), sites.sites().stream().map(CallSite::count).toList());
        }

        @Test
        void aStackWithoutOwnCodeIsCountedButNotASite() {
            CallSites sites = read(OWN, row(FIND_ORDER, null, 4, 100, RUN, EMIT, COMMIT));

            assertTrue(sites.sites().isEmpty());
            assertEquals(4, sites.totalEvents());
            assertEquals(4, sites.unattributedEvents());
        }

        @Test
        void withoutOwnCodeNothingIsASite() {
            CallSites sites = read(new FrameScope.Everything(), row(FIND_ORDER, null, 4, 100, RUN, FIND, EMIT));

            assertTrue(sites.sites().isEmpty());
            assertEquals(4, sites.unattributedEvents());
        }

        @Test
        void onePlaceIssuingTwoStatementsIsTwoRowsLongestFirst() {
            CallSites sites = read(OWN,
                    row(FIND_ORDER, null, 2, 100, RUN, FIND, EMIT),
                    row(FIND_LINES, null, 2, 900, RUN, FIND, EMIT));

            assertEquals(List.of(FIND_LINES, FIND_ORDER),
                    sites.sites().stream().map(CallSite::groupValue).toList());
        }
    }

    @Nested
    class PerTrace {

        @Test
        void countsHowOftenOneRequestIssuedTheStatement() {
            CallSites sites = read(OWN,
                    row(FIND_LINES, 7L, 24, 2_400, RUN, HANDLER, LINES_LOOP, EMIT),
                    row(FIND_LINES, 8L, 20, 2_000, RUN, HANDLER, LINES_LOOP, EMIT),
                    row(FIND_LINES, 9L, 22, 2_200, RUN, HANDLER, LINES_LOOP, EMIT));

            CallSite site = sites.sites().getFirst();
            assertEquals(66, site.count());
            assertEquals(3, site.traces());
            assertEquals(24, site.perTraceMax());
            assertEquals(22.0, site.perTraceMedian());
            assertEquals(List.of(7L, 9L, 8L), site.exemplarTraceIds());
        }

        @Test
        void namesTheHandlerARequestEnteredThroughFromItsStatementsStacks() {
            CallSites sites = read(OWN,
                    row(FIND_ORDER, 7L, 1, 100, RUN, HANDLER, FIND, EMIT),
                    row(FIND_LINES, 7L, 24, 2_400, RUN, HANDLER, LINES_LOOP, EMIT));

            assertEquals(List.of(new CallSites.OperationEntry(SHOW_ORDER,
                    new MethodKey("com.acme.web.OrderController", "show"), 25)), sites.operationEntries());
        }

        @Test
        void eventsOutsideTracesHaveNoPerTraceFigures() {
            CallSite site = read(OWN, row(FIND_ORDER, null, 3, 30, RUN, FIND, EMIT)).sites().getFirst();

            assertEquals(0, site.traces());
            assertEquals(0, site.perTraceMax());
            assertTrue(site.exemplarTraceIds().isEmpty());
        }
    }
}
