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

import cafe.jeffrey.profile.manager.model.trace.TimelineDensityBucket;
import cafe.jeffrey.profile.manager.model.trace.TimelineSpan;
import cafe.jeffrey.profile.manager.model.trace.TimelineStatePeriod;
import cafe.jeffrey.profile.manager.model.trace.TimelineTrack;
import cafe.jeffrey.profile.manager.model.trace.TimelineWindow;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineWindowAiMarkdownBuilderTest {

    private static final long MS_NANOS = 1_000_000L;
    /** 2025-01-15T10:00:00Z in epoch micros. */
    private static final long WINDOW_START = 1_736_935_200_000_000L;
    private static final long WINDOW_END = WINDOW_START + 2_000_000L;

    private static TimelineSpan span(String name, String status, long durationMs) {
        return new TimelineSpan(
                "00000000000000ff", "0000000000000001", name, "SERVER", status,
                WINDOW_START + 100_000, durationMs * MS_NANOS, 0);
    }

    private static TimelineTrack track(String thread, List<TimelineSpan> spans,
                                       List<TimelineStatePeriod> states) {
        return new TimelineTrack("1", thread, false, 1, spans, states, List.of());
    }

    private static TimelineWindow window(List<TracePause> pauses, List<TimelineTrack> tracks) {
        return new TimelineWindow(WINDOW_START, WINDOW_END, pauses, tracks, false, false, 0);
    }

    private static String render(TimelineWindow window) {
        return new TimelineWindowAiMarkdownBuilder(window).build();
    }

    @Nested
    @DisplayName("Header")
    class Header {

        @Test
        @DisplayName("states the window's bounds as UTC instants")
        void statesBounds() {
            String out = render(window(List.of(), List.of()));

            assertTrue(out.contains("window_start_utc: 2025-01-15T10:00:00Z"));
            assertTrue(out.contains("window_length: 2000ms"));
        }

        @Test
        @DisplayName("truncation is stated in the document, never silent")
        void statesTruncation() {
            TimelineWindow capped = new TimelineWindow(
                    WINDOW_START, WINDOW_END, List.of(), List.of(), true, true, 200);

            String out = render(capped);

            assertTrue(out.contains("more spans than the export cap"));
            assertTrue(out.contains("wait totals are"));
        }

        @Test
        @DisplayName("an uncapped window carries no truncation note")
        void noNoteWhenComplete() {
            String out = render(window(List.of(), List.of()));

            assertFalse(out.contains("export cap"));
        }
    }

    @Nested
    @DisplayName("Sections")
    class Sections {

        @Test
        @DisplayName("a pause is reported once with its offset into the window")
        void reportsPauses() {
            TracePause pause = new TracePause(
                    "GC_PAUSE", "G1 Evacuation Pause", WINDOW_START + 500_000, 40 * MS_NANOS);

            String out = render(window(List.of(pause), List.of()));

            assertTrue(out.contains("GC_PAUSE — G1 Evacuation Pause — 40ms at +500ms"));
        }

        @Test
        @DisplayName("an empty window says so instead of ending early")
        void emptyWindowSaysSo() {
            String out = render(window(List.of(), List.of()));

            assertTrue(out.contains("(no stop-the-world pauses crossed this window)"));
            assertTrue(out.contains("(no spans ran in this window)"));
        }

        @Test
        @DisplayName("a thread's waits are summed per category with their event count")
        void sumsWaitsPerCategory() {
            TimelineTrack track = track("worker-1",
                    List.of(span("GET /orders", "UNSET", 120)),
                    List.of(
                            new TimelineStatePeriod("PARKED", WINDOW_START, 30 * MS_NANOS),
                            new TimelineStatePeriod("PARKED", WINDOW_START + 900_000, 10 * MS_NANOS),
                            new TimelineStatePeriod("SOCKET_IO", WINDOW_START, 5 * MS_NANOS)));

            String out = render(window(List.of(), List.of(track)));

            assertTrue(out.contains("PARKED 40ms across 2 events"));
            assertTrue(out.contains("SOCKET_IO 5ms across 1 event"));
        }

        @Test
        @DisplayName("an error span is marked on its line")
        void marksErrorSpans() {
            TimelineTrack track = track("worker-1",
                    List.of(span("POST /orders", "ERROR", 200)), List.of());

            String out = render(window(List.of(), List.of(track)));

            assertTrue(out.contains("POST /orders — 200ms, SERVER, trace 00000000000000ff, ERROR"));
        }

        @Test
        @DisplayName("a density-mode track states its span count instead of reading as idle")
        void describesDensityTracks() {
            TimelineTrack dense = new TimelineTrack("1", "worker-1", false, 3, List.of(), List.of(),
                    List.of(new TimelineDensityBucket(0, 900), new TimelineDensityBucket(1, 600)));

            String out = render(window(List.of(), List.of(dense)));

            assertTrue(out.contains("1500 spans ran on this thread — too many to list"));
        }
    }
}
