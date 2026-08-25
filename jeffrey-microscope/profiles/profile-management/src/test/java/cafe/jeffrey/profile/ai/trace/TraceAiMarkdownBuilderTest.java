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
}
