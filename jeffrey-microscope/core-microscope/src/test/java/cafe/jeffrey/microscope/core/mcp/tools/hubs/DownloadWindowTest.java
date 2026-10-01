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
package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.mcp.protocol.McpFormSchema;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadWindowTest {

    private static final Instant START = Instant.parse("2026-03-01T06:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final long AT = Instant.parse("2026-03-01T09:00:00Z").toEpochMilli();

    /** A session the rule must not read: a window that needs nothing of it costs no call to the hub. */
    private static final Supplier<WindowSubject> NEVER_READ = () -> {
        throw new AssertionError("the session was read for a window that does not need it");
    };

    private static WindowArguments minutes(Integer minutes) {
        return new WindowArguments(minutes, null, null, null);
    }

    private static WindowArguments at(long atEpochMs, Integer minutes) {
        return new WindowArguments(minutes, atEpochMs, null, null);
    }

    private static WindowArguments bounds(Long start, Long end) {
        return new WindowArguments(null, null, start, end);
    }

    private static ChunkedSessions twelveChunks(long minutes) {
        return ChunkedSessions.startingAt(START, Duration.ofMinutes(minutes))
                .compressed(10, 20, 90, 30, 20, 10, 20, 30, 40, 10, 20, 30);
    }

    @Nested
    class SessionFree {

        @Test
        void wholeIsTheWholeSession() {
            assertInstanceOf(WindowResolution.Whole.class,
                    DownloadWindow.WHOLE.resolve(WindowArguments.NONE, NEVER_READ));
        }

        @Test
        void beforeIsTheMinutesEndingAtTheMoment() {
            WindowResolution.Span span = assertInstanceOf(WindowResolution.Span.class,
                    DownloadWindow.BEFORE.resolve(at(AT, 30), NEVER_READ));

            assertEquals(Instant.ofEpochMilli(AT).minus(Duration.ofMinutes(30)), span.window().start());
            assertEquals(Instant.ofEpochMilli(AT), span.window().end());
        }

        @Test
        void beforeTakesTheLastHourWhenNoMinutesAreGiven() {
            WindowResolution.Span span = assertInstanceOf(WindowResolution.Span.class,
                    DownloadWindow.BEFORE.resolve(at(AT, null), NEVER_READ));

            assertEquals(Duration.ofMinutes(DownloadWindow.DEFAULT_MINUTES),
                    Duration.between(span.window().start(), span.window().end()));
        }

        /** Odd minutes stay exact: half of 15 minutes either side, to the millisecond. */
        @Test
        void aroundIsCentredOnTheMoment() {
            WindowResolution.Span span = assertInstanceOf(WindowResolution.Span.class,
                    DownloadWindow.AROUND.resolve(at(AT, 15), NEVER_READ));

            assertEquals(AT - Duration.ofSeconds(450).toMillis(), span.window().start().toEpochMilli());
            assertEquals(AT + Duration.ofSeconds(450).toMillis(), span.window().end().toEpochMilli());
        }

        @Test
        void customKeepsAnOpenBound() {
            WindowResolution.Span span = assertInstanceOf(WindowResolution.Span.class,
                    DownloadWindow.CUSTOM.resolve(bounds(AT, null), NEVER_READ));

            assertEquals(Instant.ofEpochMilli(AT), span.window().start());
            assertNull(span.window().end());
        }

        @Test
        void customEndingBeforeItStartsIsRefused() {
            assertInstanceOf(WindowResolution.Refused.class,
                    DownloadWindow.CUSTOM.resolve(bounds(AT, AT - 1), NEVER_READ));
        }

        @Test
        void minutesOutOfRangeAreRefused() {
            assertInstanceOf(WindowResolution.Refused.class,
                    DownloadWindow.BEFORE.resolve(at(AT, DownloadWindow.MAX_WINDOW_MINUTES + 1), NEVER_READ));
        }
    }

    @Nested
    class OnTheSession {

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void lastMinutesEndAtTheSessionsFinish(long minutes) {
            ChunkedSessions session = twelveChunks(minutes);

            WindowResolution.Span span = assertInstanceOf(WindowResolution.Span.class,
                    DownloadWindow.LAST_MINUTES.resolve(minutes(10), () -> session.subject(NOW)));

            assertEquals(session.end(), span.window().end());
            assertEquals(session.end().minus(Duration.ofMinutes(10)), span.window().start());
        }

        /** Reaching back past the start of a finished session is all of it. */
        @Test
        void lastMinutesCoveringAFinishedSessionAreTheWholeSession() {
            ChunkedSessions session = twelveChunks(5);

            assertInstanceOf(WindowResolution.Whole.class,
                    DownloadWindow.LAST_MINUTES.resolve(minutes(600), () -> session.subject(NOW)));
        }

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void startupIsTheFirstChunkByItsId(long minutes) {
            WindowResolution.OneChunk one = assertInstanceOf(WindowResolution.OneChunk.class,
                    DownloadWindow.STARTUP.resolve(WindowArguments.NONE, () -> twelveChunks(minutes).subject(NOW)));

            assertEquals(ChunkedSessions.fileId(0), one.fileId());
            assertEquals(Duration.ofMinutes(minutes).toMillis(), one.chosen().chunkLengthMs());
        }

        @ParameterizedTest
        @ValueSource(longs = {5, 15})
        void startupRemovedByTheCleanerIsNotRetainedAndNamesTheOldestKept(long minutes) {
            WindowResolution.NotRetained gone = assertInstanceOf(WindowResolution.NotRetained.class,
                    DownloadWindow.STARTUP.resolve(WindowArguments.NONE,
                            () -> twelveChunks(minutes).withoutOldest(4).subject(NOW)));

            assertEquals(ChunkedSessions.fileId(4), gone.chosen().chunkFileId());
            assertTrue(gone.reason().contains("profile-4"), gone.reason());
        }

        @Test
        void latestIsTheNewestFinishedChunk() {
            WindowResolution.OneChunk one = assertInstanceOf(WindowResolution.OneChunk.class,
                    DownloadWindow.LATEST.resolve(WindowArguments.NONE, () -> twelveChunks(15).live().subject(NOW)));

            assertEquals(ChunkedSessions.fileId(11), one.fileId());
        }

        @Test
        void peakIsTheLargestCompressedChunkWithItsEvidence() {
            WindowResolution.OneChunk one = assertInstanceOf(WindowResolution.OneChunk.class,
                    DownloadWindow.PEAK.resolve(WindowArguments.NONE, () -> twelveChunks(15).subject(NOW)));

            assertEquals(ChunkedSessions.fileId(2), one.fileId());
            assertEquals(90L, one.chosen().chunkSizeBytes());
            assertEquals(20L, one.chosen().medianChunkSizeBytes());
            assertTrue(one.chosen().evidence().contains("4.5×"), one.chosen().evidence());
        }

        @Test
        void peakWithNothingCompressedIsRefused() {
            ChunkedSessions raw = ChunkedSessions.startingAt(START, Duration.ofMinutes(15)).raw(10, 20);

            assertInstanceOf(WindowResolution.Refused.class,
                    DownloadWindow.PEAK.resolve(WindowArguments.NONE, () -> raw.subject(NOW)));
        }
    }

    @Nested
    class Arguments {

        @Test
        void aWindowTakingNothingRefusesMinutes() {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> DownloadWindow.PEAK.checkArguments(minutes(10)));

            assertTrue(refused.getMessage().contains("LAST_MINUTES, BEFORE, AROUND"), refused.getMessage());
        }

        @Test
        void beforeNeedsTheMoment() {
            assertThrows(IllegalArgumentException.class, () -> DownloadWindow.BEFORE.checkArguments(minutes(10)));
        }

        @Test
        void customNeedsABound() {
            assertThrows(IllegalArgumentException.class,
                    () -> DownloadWindow.CUSTOM.checkArguments(WindowArguments.NONE));
        }

        @Test
        void customRefusesTheMoment() {
            assertThrows(IllegalArgumentException.class,
                    () -> DownloadWindow.CUSTOM.checkArguments(new WindowArguments(null, AT, AT, null)));
        }

        @Test
        void minutesWithoutAWindowAreRefused() {
            assertThrows(IllegalArgumentException.class, () -> DownloadWindow.checkWithoutWindow(minutes(10)));
        }

        /** Bounds without a window have always meant a custom span. */
        @Test
        void boundsWithoutAWindowAreFine() {
            DownloadWindow.checkWithoutWindow(bounds(AT, null));
        }
    }

    @Nested
    class Choices {

        @Test
        void aSessionWithEveryChunkOffersEveryWindow() {
            WindowSubject subject = twelveChunks(15).subject(NOW);

            for (DownloadWindow window : DownloadWindow.values()) {
                McpFormSchema.Choice choice = window.choice(subject).orElseThrow();
                assertEquals(window.name(), choice.value());
            }
        }

        @Test
        void startupIsNotOfferedOnceTheCleanerRemovedIt() {
            assertTrue(DownloadWindow.STARTUP.choice(twelveChunks(15).withoutOldest(3).subject(NOW)).isEmpty());
        }

        @Test
        void peakIsNotOfferedWithNothingCompressed() {
            WindowSubject raw = ChunkedSessions.startingAt(START, Duration.ofMinutes(15)).raw(10, 20).subject(NOW);

            assertTrue(DownloadWindow.PEAK.choice(raw).isEmpty());
        }

        @Test
        void peakIsTitledWithItsSpanSizeAndRatio() {
            String title = DownloadWindow.PEAK.choice(twelveChunks(15).subject(NOW)).orElseThrow().title();

            assertTrue(title.startsWith("Peak (06:30–06:45 UTC"), title);
            assertTrue(title.contains("4.5× median"), title);
        }
    }
}
