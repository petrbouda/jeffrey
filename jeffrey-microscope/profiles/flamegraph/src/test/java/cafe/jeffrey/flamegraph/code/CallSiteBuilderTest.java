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
import cafe.jeffrey.provider.profile.api.CallSiteReader;
import cafe.jeffrey.provider.profile.api.CallSiteRow;
import cafe.jeffrey.provider.profile.api.OperationStackRow;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    private static JfrStackTrace stack(long id, JfrStackFrame... rootFirst) {
        return new TestStackTrace(id, Arrays.asList(rootFirst));
    }

    /** A row of events outside traces. */
    private static CallSiteRow row(int path, String group, long count, long nanos) {
        return new CallSiteRow(path, group, group == null ? null : group.length(), group, count, nanos,
                nanos / Math.max(1, count), 0, 0, 0, List.of());
    }

    private static CallSiteBuilder builder(FrameScope scope) {
        return new CallSiteBuilder(scope);
    }

    @Nested
    class Paths {

        @Test
        void aStackIsChargedToItsOwnFramesBelowTheTracingFrames() {
            CallSiteBuilder builder = builder(OWN);
            int path = builder.pathOf(stack(1, RUN, HANDLER, FIND, EMIT, COMMIT));
            builder.onSite(row(path, FIND_ORDER, 10, 5_000));

            CallSite site = builder.build().sites().getFirst();
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
        void stacksSharingTheirOwnFramesShareAPath() {
            CallSiteBuilder builder = builder(OWN);
            JfrStackFrame proxy = frame("jdk.proxy2.$Proxy12", "executeQuery", 0);

            assertEquals(builder.pathOf(stack(1, RUN, HANDLER, FIND, EMIT)),
                    builder.pathOf(stack(2, RUN, HANDLER, FIND, proxy, EMIT)));
        }

        @Test
        void anOwnWrapperCalledFromTwoPlacesIsTwoPaths() {
            JfrStackFrame wrapper = frame("com.acme.db.DatabaseClient", "query", 170);
            JfrStackFrame other = frame("com.acme.orders.InvoiceRepository", "findAll", 55);
            CallSiteBuilder builder = builder(OWN);
            int fromFind = builder.pathOf(stack(1, RUN, FIND, wrapper, EMIT));
            int fromOther = builder.pathOf(stack(2, RUN, other, wrapper, EMIT));
            builder.onSite(row(fromFind, FIND_ORDER, 3, 300));
            builder.onSite(row(fromOther, FIND_ORDER, 1, 100));

            assertNotEquals(fromFind, fromOther);
            CallSites sites = builder.build();
            assertEquals(2, sites.sites().size());
            assertTrue(sites.sites().stream().allMatch(site -> site.method().methodName().equals("query")));
            assertEquals(List.of(3L, 1L), sites.sites().stream().map(CallSite::count).toList());
        }

        @Test
        void aStackWithoutOwnCodeIsCountedButNotASite() {
            CallSiteBuilder builder = builder(OWN);
            int path = builder.pathOf(stack(1, RUN, EMIT, COMMIT));
            builder.onSite(row(path, FIND_ORDER, 4, 100));

            assertEquals(CallSiteReader.UNATTRIBUTED, path);
            CallSites sites = builder.build();
            assertTrue(sites.sites().isEmpty());
            assertEquals(4, sites.totalEvents());
            assertEquals(4, sites.unattributedEvents());
        }

        @Test
        void withoutOwnCodeNothingIsASite() {
            assertEquals(CallSiteReader.UNATTRIBUTED,
                    builder(new FrameScope.Everything()).pathOf(stack(1, RUN, FIND, EMIT)));
        }

        @Test
        void onePlaceIssuingTwoStatementsIsTwoSitesLongestFirst() {
            CallSiteBuilder builder = builder(OWN);
            int path = builder.pathOf(stack(1, RUN, FIND, EMIT));
            builder.onSite(row(path, FIND_ORDER, 2, 100));
            builder.onSite(row(path, FIND_LINES, 2, 900));

            assertEquals(List.of(FIND_LINES, FIND_ORDER),
                    builder.build().sites().stream().map(CallSite::groupValue).toList());
        }
    }

    @Nested
    class PerTrace {

        @Test
        void aSiteCarriesTheDatabasesPerTraceFigures() {
            CallSiteBuilder builder = builder(OWN);
            int path = builder.pathOf(stack(1, RUN, HANDLER, LINES_LOOP, EMIT));
            builder.onSite(new CallSiteRow(path, FIND_LINES, FIND_LINES.length(), FIND_LINES, 66, 6_600, 100,
                    3, 24, 22.0, List.of(7L, 9L, 8L)));

            CallSite site = builder.build().sites().getFirst();
            assertEquals(66, site.count());
            assertEquals(3, site.traces());
            assertEquals(24, site.perTraceMax());
            assertEquals(22.0, site.perTraceMedian());
            assertEquals(List.of(7L, 9L, 8L), site.exemplarTraceIds());
        }

        @Test
        void namesTheHandlerARequestEnteredThroughFromItsStatementsStacks() {
            CallSiteBuilder builder = builder(OWN);
            JfrStackTrace find = stack(1, RUN, HANDLER, FIND, EMIT);
            JfrStackTrace lines = stack(2, RUN, HANDLER, LINES_LOOP, EMIT);
            builder.pathOf(find);
            builder.pathOf(lines);
            builder.onOperationStack(new OperationStackRow(find, SHOW_ORDER, 1));
            builder.onOperationStack(new OperationStackRow(lines, SHOW_ORDER, 24));

            assertEquals(List.of(new CallSites.OperationEntry(SHOW_ORDER,
                    new MethodKey("com.acme.web.OrderController", "show"), 25)), builder.build().operationEntries());
        }

        @Test
        void aStackWithoutOwnCodeNamesNoEntry() {
            CallSiteBuilder builder = builder(OWN);
            JfrStackTrace foreign = stack(1, RUN, EMIT, COMMIT);
            builder.pathOf(foreign);
            builder.onOperationStack(new OperationStackRow(foreign, SHOW_ORDER, 5));

            assertTrue(builder.build().operationEntries().isEmpty());
        }
    }
}
