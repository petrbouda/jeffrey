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

package cafe.jeffrey.profile.ai.trace;

import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceExceptionRow;
import cafe.jeffrey.profile.manager.model.trace.TraceContextSlice;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceAiMarkdownBuilderTest {

    private static final long MS = 1_000_000L;

    /**
     * The worked example the whole feature exists for: a span that looks slow because of its own
     * code, and is not. reserveInventory runs 230ms, of which 105ms is its two queries and 100ms is
     * a collection pause.
     */
    private static TraceDetail detail() {
        TraceRow trace = new TraceRow(
                "7f3a91", "POST /api/orders", "SERVER", "jeffrey.HttpServerExchange",
                60_000, 1_700_000_000_000L, 420 * MS, 4, 0, true);

        List<TraceSpanRow> spans = List.of(
                span("01", null, "POST /api/orders", "SERVER", 0, 420, 27, 420, 0),
                span("02", "01", "reserveInventory", "INTERNAL", 100, 230, 125, 230, 1),
                span("03", "02", "select inventory", "CLIENT", 105, 35, 35, 0, 2),
                span("04", "02", "update inventory", "CLIENT", 250, 70, 70, 0, 2));

        return new TraceDetail(trace, spans, List.of(), List.of(), Map.of());
    }

    private static TraceSpanRow span(
            String spanId, String parentSpanId, String name, String kind,
            long startMillis, long durationMs, long selfMs, long criticalMs, int depth) {

        return new TraceSpanRow(
                spanId, parentSpanId, name, kind, "UNSET", null,
                startMillis, startMillis * 1_000L, durationMs * MS, selfMs * MS, criticalMs * MS,
                depth, "3001", "http-1", false, "jeffrey.TraceSpan", null, null, false);
    }

    private static TraceContext context() {
        return new TraceContext(
                List.of(new TracePause("GC_PAUSE", "G1 Young", 145_000L, 100 * MS, false)),
                // The markdown export does not read throttle windows: they explain a trace rather
                // than accounting for it, and the summary below is what the export renders.
                List.of(),
                Map.of("02", List.of(new TraceContextSlice("MONITOR_BLOCKED", 4 * MS, 1))),
                List.of(
                        new TraceContextSlice("GC_PAUSE", 100 * MS, 1),
                        new TraceContextSlice("MONITOR_BLOCKED", 4 * MS, 1),
                        new TraceContextSlice("OWN_WORK", 316 * MS, 0)));
    }

    private static String build() {
        return new TraceAiMarkdownBuilder(detail(), context()).build();
    }

    @Nested
    @DisplayName("Preamble")
    class Preamble {

        /*
         * These read as tautologies and are not. The preamble is the feature: a model handed
         * "self 125ms" without being told how self time is computed will reason about it as a
         * subtraction and reach a confident wrong conclusion. Losing a caveat during an unrelated
         * edit is exactly how this export starts misleading people, and nothing else would catch it.
         */

        @Test
        @DisplayName("explains that self time is a merged interval, not a subtraction")
        void explainsSelfTimeSemantics() {
            String out = build();

            assertTrue(out.contains("concurrently"), "must say concurrent children are counted once");
            assertTrue(out.contains("same thread"), "must say only same-thread children are subtracted");
        }

        @Test
        @DisplayName("carries the critical-path caveat with the marker it explains")
        void explainsCriticalPathCaveat() {
            String out = build();

            assertTrue(out.contains("!critical"));
            assertTrue(out.contains("Caveat:"), "the cross-thread assumption must travel with it");
            assertTrue(out.contains("forked"), "must name the case where attribution is a hint only");
        }

        @Test
        @DisplayName("separates global pauses from thread-scoped waits")
        void explainsContextScopes() {
            String out = build();

            assertTrue(out.contains("Global"));
            assertTrue(out.contains("Thread-scoped"));
            assertTrue(out.contains("summing across the groups is not"),
                    "a model must be told these are different kinds of measurement");
        }

        @Test
        @DisplayName("says what the residual is, so waiting is not mistaken for the whole story")
        void explainsResidual() {
            assertTrue(build().contains("OWN_WORK"));
        }

        @Test
        @DisplayName("states the accounting invariant against the trace window, not the span sum")
        void statesInvariant() {
            String out = build();

            assertTrue(out.contains("nest"), "must warn that spans nest rather than sum");
            assertTrue(out.contains("trace's own window"));
        }
    }

    @Nested
    @DisplayName("Span tree")
    class Tree {

        @Test
        @DisplayName("reports self and total as different numbers")
        void reportsSelfAndTotal() {
            // The whole finding rests on these being different: 230ms total, 125ms self.
            assertTrue(build().contains("230ms (55%, self 125ms)"));
        }

        @Test
        @DisplayName("encodes depth as indentation, so span ids never have to be resolved")
        void indentsByDepth() {
            String out = build();

            assertTrue(out.contains("\n- POST /api/orders"), "the root is unindented");
            assertTrue(out.contains("\n  - reserveInventory"), "its child is one level in");
            assertTrue(out.contains("\n    - select inventory"), "a grandchild is two");
        }

        @Test
        @DisplayName("marks the critical path and leaves the rest unmarked")
        void marksCriticalPath() {
            String out = build();

            assertTrue(out.contains("reserveInventory [INTERNAL] — 230ms (55%, self 125ms) !critical"));
            assertFalse(out.contains("update inventory [CLIENT] — 70ms (17%, self 70ms) !critical"),
                    "a span off the critical path must not be marked");
        }

        @Test
        @DisplayName("names an error and its type")
        void marksErrors() {
            TraceSpanRow failing = new TraceSpanRow(
                    "05", "01", "chargePayment", "CLIENT", "ERROR", "TimeoutException",
                    335, 335_000L, 65 * MS, 65 * MS, 0, 1, "3001", "http-1", false,
                    "jeffrey.TraceSpan", null, null, false);
            TraceDetail withError = new TraceDetail(
                    detail().trace(), List.of(failing), List.of(), List.of(), Map.of());

            String out = new TraceAiMarkdownBuilder(withError, context()).build();

            assertTrue(out.contains("!error (TimeoutException)"));
        }

        @Test
        @DisplayName("states what it dropped rather than stopping silently")
        void annotatesTruncation() {
            List<TraceSpanRow> many = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                many.add(span("s" + i, null, "span-" + i, "INTERNAL", i, 1, 1, 0, 0));
            }
            TraceDetail huge = new TraceDetail(detail().trace(), many, List.of(), List.of(), Map.of());

            String out = new TraceAiMarkdownBuilder(huge, context()).build();

            // A bundle that quietly stops reads as a complete trace, and a model will conclude the
            // missing spans do not exist.
            assertTrue(out.contains("truncated: 100 further spans"));
            assertTrue(out.contains("out of 500"));
        }
    }

    @Nested
    @DisplayName("JVM context")
    class Context {

        @Test
        @DisplayName("names the pause and what it was")
        void rendersPauses() {
            String out = build();

            assertTrue(out.contains("GC_PAUSE (G1 Young) — 100ms"));
        }

        @Test
        @DisplayName("attributes thread-scoped waiting to the span it happened in")
        void rendersSpanWaits() {
            assertTrue(build().contains("reserveInventory — MONITOR_BLOCKED 4ms"));
        }

        @Test
        @DisplayName("ranks where the time went, labelling the residual")
        void rendersRanking() {
            String out = build();

            assertTrue(out.contains("GC_PAUSE — 100ms (24%)"));
            assertTrue(out.contains("OWN_WORK — 316ms (75%) — the residual"));
        }

        @Test
        @DisplayName("a trace the JVM never interrupted still renders")
        void survivesEmptyContext() {
            String out = new TraceAiMarkdownBuilder(detail(), TraceContext.EMPTY).build();

            assertTrue(out.contains("no GC pauses, safepoints or blocking events"));
            assertTrue(out.contains("reserveInventory"), "the tree is still the point");
        }

        @Test
        @DisplayName("a trace with no spans does not produce a broken document")
        void survivesEmptyTrace() {
            TraceDetail empty = new TraceDetail(detail().trace(), List.of(), List.of(), List.of(), Map.of());

            assertTrue(new TraceAiMarkdownBuilder(empty, TraceContext.EMPTY).build()
                    .contains("this trace has no spans"));
        }
    }

    /**
     * A promoted I/O span: the shape the derivation gives a {@code jdk.FileRead} and friends, with
     * the event's own payload still in {@code eventFields} — which is where the bytes and the target
     * come from.
     */
    private static TraceSpanRow ioSpan(
            String spanId, String name, String eventType, String eventFields, long durationNanos) {

        return new TraceSpanRow(
                spanId, "01", name, "INTERNAL", "UNSET", null,
                0, 0L, durationNanos, durationNanos, 0,
                1, "3001", "http-1", false, eventType, null, eventFields, true);
    }

    private static TraceExceptionRow thrown(
            String spanId, String exceptionId, String thrownClass, String message, boolean escaped) {

        return new TraceExceptionRow(
                spanId, exceptionId, 0, 0L, "jdk.JavaExceptionThrow",
                thrownClass, message, escaped, "st1", "3001");
    }

    private static String buildWith(List<TraceSpanRow> spans, List<TraceExceptionRow> exceptions) {
        TraceDetail detail = new TraceDetail(detail().trace(), spans, List.of(), exceptions, Map.of());
        return new TraceAiMarkdownBuilder(detail, context()).build();
    }

    /**
     * The one line of the document that mentions {@code needle}.
     * <p>
     * Markers have to be asserted against the row that carries them, never against the whole
     * document: the preamble defines every marker it explains, so {@code out.contains("!small-ops")}
     * passes on a document where nothing was ever marked.
     */
    private static String lineWith(String out, String needle) {
        return out.lines()
                .filter(line -> line.contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line contains: " + needle));
    }

    @Nested
    @DisplayName("I/O operations")
    class Io {

        /** Twenty 512-byte reads of one file: what a missing BufferedInputStream looks like. */
        private List<TraceSpanRow> unbufferedReads() {
            List<TraceSpanRow> spans = new ArrayList<>();
            spans.add(span("01", null, "loadClasses", "INTERNAL", 0, 420, 20, 420, 0));
            for (int i = 0; i < 20; i++) {
                spans.add(ioSpan("f" + i, "File read", "jdk.FileRead",
                        "{\"path\":\"/data/dump.hprof\",\"bytesRead\":512}", 3 * MS));
            }
            return spans;
        }

        @Test
        @DisplayName("groups operations by target and reports the mean, which is the buffering figure")
        void reportsMeanPerOperation() {
            String out = buildWith(unbufferedReads(), List.of());

            assertTrue(out.contains("File read /data/dump.hprof — 20 ops"));
            assertTrue(out.contains("mean 512 B/op"), "the mean is the whole finding");
            assertTrue(out.contains("bytes 10.0 KiB (10240 B)"),
                    "the exact byte count travels with the rounded one, so 4096 and 4600 stay apart");
        }

        @Test
        @DisplayName("marks the small-operation shape on the row it applies to")
        void marksSmallOperations() {
            String row = lineWith(buildWith(unbufferedReads(), List.of()), "File read /data/dump.hprof");

            assertTrue(row.endsWith("!small-ops"), row);
        }

        @Test
        @DisplayName("leaves a well-buffered target unmarked")
        void leavesBufferedTargetUnmarked() {
            List<TraceSpanRow> spans = new ArrayList<>();
            spans.add(span("01", null, "writeIndex", "INTERNAL", 0, 420, 20, 420, 0));
            for (int i = 0; i < 20; i++) {
                spans.add(ioSpan("w" + i, "File write", "jdk.FileWrite",
                        "{\"path\":\"/data/index.db\",\"bytesWritten\":1048576}", 3 * MS));
            }

            String row = lineWith(buildWith(spans, List.of()), "File write /data/index.db");

            assertTrue(row.contains("File write /data/index.db — 20 ops"));
            assertFalse(row.contains("!small-ops"), "a megabyte per write is not an unbuffered stream");
        }

        @Test
        @DisplayName("reports a socket against its peer rather than a path")
        void namesSocketPeer() {
            List<TraceSpanRow> spans = List.of(
                    span("01", null, "query", "INTERNAL", 0, 420, 20, 420, 0),
                    ioSpan("s1", "Socket read", "jdk.SocketRead",
                            "{\"host\":\"db.internal\",\"port\":5432,\"bytesRead\":180}", 40 * MS));

            assertTrue(buildWith(spans, List.of()).contains("Socket read db.internal:5432 — 1 op"));
        }

        @Test
        @DisplayName("reports an fsync without bytes it never moved")
        void omitsBytesForFsync() {
            List<TraceSpanRow> spans = List.of(
                    span("01", null, "commit", "INTERNAL", 0, 420, 20, 420, 0),
                    ioSpan("q1", "File force", "jdk.FileForce",
                            "{\"path\":\"/data/index.db\"}", 2 * MS));

            String row = lineWith(buildWith(spans, List.of()), "File force /data/index.db");

            assertTrue(row.contains("File force /data/index.db — 1 op · time 2ms"), row);
            assertFalse(row.contains("bytes"),
                    "0 B against an fsync reads as a lost byte count rather than as none");
        }

        /*
         * The case the section earns its keep in: a trace big enough to truncate the tree is exactly
         * the one whose I/O is worth totalling, and the totals must not stop where the bullets did.
         */
        @Test
        @DisplayName("counts every operation, including ones the span tree had to truncate")
        void countsBeyondTheTruncatedTree() {
            List<TraceSpanRow> spans = new ArrayList<>();
            spans.add(span("01", null, "loadClasses", "INTERNAL", 0, 420, 20, 420, 0));
            for (int i = 0; i < 500; i++) {
                spans.add(ioSpan("f" + i, "File read", "jdk.FileRead",
                        "{\"path\":\"/data/dump.hprof\",\"bytesRead\":512}", MS));
            }

            String out = buildWith(spans, List.of());

            assertTrue(out.contains("truncated: 101 further spans"), "the tree still stops at 400");
            assertTrue(out.contains("File read /data/dump.hprof — 500 ops"),
                    "the accounting must not stop where the bullets did");
        }

        @Test
        @DisplayName("says the counts are lower bounds, because JFR drops fast operations")
        void statesThresholdCaveat() {
            String out = buildWith(unbufferedReads(), List.of());

            assertTrue(out.contains("I/O threshold"));
            assertTrue(out.contains("lower bounds"),
                    "a model must not read a threshold-filtered count as a measurement");
        }

        @Test
        @DisplayName("a trace that touched no I/O says so rather than showing an empty section")
        void survivesNoIo() {
            assertTrue(build().contains("no socket or file operations were recorded"));
        }
    }

    @Nested
    @DisplayName("Exceptions")
    class Exceptions {

        private List<TraceExceptionRow> throws42AndOneEscape() {
            List<TraceExceptionRow> exceptions = new ArrayList<>();
            for (int i = 0; i < 42; i++) {
                exceptions.add(thrown("02", "e" + i, "java.io.FileNotFoundException",
                        "/data/Foo.class (No such file or directory)", false));
            }
            exceptions.add(thrown("04", "e99", "java.util.concurrent.TimeoutException",
                    "update timed out", true));
            return exceptions;
        }

        @Test
        @DisplayName("groups repeated throws into one finding with a count")
        void groupsByClassAndMessage() {
            String out = buildWith(detail().spans(), throws42AndOneEscape());

            assertTrue(out.contains("java.io.FileNotFoundException ×42"),
                    "42 lines read as 42 problems; one line with a count reads as the pattern it is");
            assertTrue(out.contains("message: /data/Foo.class (No such file or directory)"));
        }

        @Test
        @DisplayName("separates the throw that failed a span from the ones that were caught")
        void marksEscaped() {
            String out = buildWith(detail().spans(), throws42AndOneEscape());

            assertTrue(out.contains("43 throws recorded, 1 of which escaped its span."));
            assertTrue(lineWith(out, "TimeoutException ×1").endsWith("!escaped"));
            assertFalse(lineWith(out, "FileNotFoundException ×42").contains("!escaped"),
                    "a caught throw is a cost, not a failure, and must not be marked as one");
        }

        @Test
        @DisplayName("says plainly when nothing escaped, so volume is not read as breakage")
        void namesTheAllCaughtCase() {
            List<TraceExceptionRow> caught = List.of(
                    thrown("02", "e1", "java.lang.NumberFormatException", "For input string: \"\"", false));

            assertTrue(buildWith(detail().spans(), caught)
                    .contains("none of which escaped its span — every one was caught"));
        }

        @Test
        @DisplayName("attributes throws to span names rather than to hex ids")
        void namesTheSpansThatThrew() {
            String out = buildWith(detail().spans(), throws42AndOneEscape());

            assertTrue(out.contains("thrown in: reserveInventory ×42"));
            assertTrue(out.contains("thrown in: update inventory ×1"));
        }

        /* A message carrying an embedded stack trace would otherwise end the bullet it sits in. */
        @Test
        @DisplayName("flattens a multi-line message onto its own line")
        void flattensMessages() {
            List<TraceExceptionRow> multiline = List.of(
                    thrown("02", "e1", "java.lang.IllegalStateException", "broke\n\tat Foo.bar(Foo.java:1)", false));

            String out = buildWith(detail().spans(), multiline);

            assertTrue(out.contains("message: broke at Foo.bar(Foo.java:1)"));
        }

        @Test
        @DisplayName("a trace that threw nothing says so rather than showing an empty section")
        void survivesNoThrows() {
            assertTrue(build().contains("no exceptions or errors were recorded"));
        }

        @Test
        @DisplayName("the preamble says a throw is not by itself a failure")
        void explainsThrowSemantics() {
            String out = build();

            assertTrue(out.contains("A throw is not a failure."));
            assertTrue(out.contains("!escaped"), "the marker the rule is about must travel with it");
            assertTrue(out.contains("control flow"),
                    "the exceptions-as-control-flow reading is the one a count is for");
        }
    }
}
