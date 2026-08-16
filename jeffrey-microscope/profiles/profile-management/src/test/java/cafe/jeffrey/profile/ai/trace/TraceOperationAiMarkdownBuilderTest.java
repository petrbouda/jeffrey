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

import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceOperationAiMarkdownBuilderTest {

    private static final long MS = 1_000_000L;

    private static TraceOperationRow operation() {
        return new TraceOperationRow(
                "POST /api/orders", "SERVER", "jeffrey.HttpServerExchange",
                214, 3, 1_712, 40_000 * MS, 180 * MS, 320 * MS, 410 * MS, 980 * MS);
    }

    /**
     * The two rankings deliberately disagree, which is the case the document exists to make legible:
     * reserveInventory is the largest inclusive figure and a small self one, because it mostly waits
     * on the two statements beneath it.
     */
    private static TraceOperationSummary summary() {
        return new TraceOperationSummary(
                List.of(
                        span("reserveInventory", 900, 4_500, 20, 210),
                        span("update inventory", 1_400, 1_400, 70, 70),
                        span("select inventory", 800, 800, 35, 35)),
                new TraceOperationThreads(8, 1_700, 12, 0));
    }

    private static TraceOperationSpanRow span(
            String name, long selfMs, long inclusiveMs, long p50SelfMs, long p50Ms) {

        return new TraceOperationSpanRow(
                name, 214, 214, inclusiveMs * MS, selfMs * MS, p50Ms * MS, p50SelfMs * MS, 400 * MS);
    }

    private static List<TraceRow> slowest() {
        return List.of(new TraceRow(
                "7f3a91", "POST /api/orders", "SERVER", "jeffrey.HttpServerExchange",
                60_000, 1_700_000_000_000L, 980 * MS, 12, 1, true));
    }

    private static String build() {
        return new TraceOperationAiMarkdownBuilder(operation(), summary(), slowest()).build();
    }

    @Nested
    @DisplayName("Preamble")
    class Preamble {

        @Test
        @DisplayName("warns that inclusive figures sum past the total and self figures sum to it")
        void explainsTheTwoAccountings() {
            String out = build();

            assertTrue(out.contains("sum to more than"), "inclusive over-sums by construction");
            assertTrue(out.contains("do sum to"), "self is the comparable one");
        }

        @Test
        @DisplayName("says what ranking by the wrong column costs")
        void explainsWhyItMatters() {
            assertTrue(build().contains("optimising a method that does nothing but call other methods"));
        }

        @Test
        @DisplayName("repeats how self time is computed, since this document can be read alone")
        void repeatsSelfTimeSemantics() {
            String out = build();

            assertTrue(out.contains("merging child intervals"));
            assertTrue(out.contains("different thread"));
        }

        @Test
        @DisplayName("frames percentiles as a population and warns off max")
        void explainsPercentiles() {
            String out = build();

            assertTrue(out.contains("population"));
            assertTrue(out.contains("single observation"), "max must not be read as typical");
        }

        @Test
        @DisplayName("says what it cannot answer, and where to go instead")
        void statesItsLimits() {
            String out = build();

            assertTrue(out.contains("no per-trace JVM context"));
            assertTrue(out.contains("single-trace export"));
        }
    }

    @Nested
    @DisplayName("Span breakdown")
    class Spans {

        @Test
        @DisplayName("ranks by self time, not by the inclusive figure the server sorted on")
        void ranksBySelf() {
            String out = build();
            int updateInventory = out.indexOf("- update inventory");
            int reserveInventory = out.indexOf("- reserveInventory");

            assertTrue(updateInventory < reserveInventory,
                    "update inventory does more real work despite a smaller inclusive total");
        }

        @Test
        @DisplayName("carries both accountings on the same line so the disagreement is visible")
        void carriesBothFigures() {
            // Both in milliseconds: the wide ms band exists so figures meant to be compared with
            // each other carry the same unit.
            assertTrue(build().contains(
                    "reserveInventory — self 900ms (median 20ms), inclusive 4500ms (median 210ms)"));
        }

        @Test
        @DisplayName("reports occurrences, so an N+1 pattern is visible")
        void reportsOccurrences() {
            assertTrue(build().contains("214 occurrences in 214 traces"));
        }

        @Test
        @DisplayName("an operation with no spans still renders")
        void survivesEmptySpans() {
            TraceOperationSummary empty = new TraceOperationSummary(
                    List.of(), new TraceOperationThreads(0, 0, 0, 0));

            assertTrue(new TraceOperationAiMarkdownBuilder(operation(), empty, List.of()).build()
                    .contains("no spans recorded"));
        }
    }

    @Nested
    @DisplayName("Header and exemplars")
    class HeaderAndExemplars {

        @Test
        @DisplayName("reports the percentile spread that the analysis instruction asks about")
        void reportsPercentiles() {
            String out = build();

            assertTrue(out.contains("p50: 180ms"));
            assertTrue(out.contains("p99: 410ms"));
            assertTrue(out.contains("max: 980ms"));
        }

        @Test
        @DisplayName("names traces worth exporting individually")
        void namesExemplars() {
            String out = build();

            assertTrue(out.contains("7f3a91 — 980ms, 12 spans, 1 error"));
            assertTrue(out.contains("worth exporting on their own"));
        }

        @Test
        @DisplayName("names unresolved threads rather than hiding them in a zero")
        void reportsUnknownThreads() {
            assertTrue(build().contains("unknown_thread_spans: 0"));
        }
    }
}
