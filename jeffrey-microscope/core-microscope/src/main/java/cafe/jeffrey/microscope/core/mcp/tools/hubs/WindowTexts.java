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

import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.ChosenWindow;
import cafe.jeffrey.microscope.model.repository.ChunkWindow.Selection.Chunk;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * What the window rules share: the minutes a window was given, and how a time or a chunk is written
 * in a form title or an evidence line.
 */
final class WindowTexts {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_AND_CLOCK =
            DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    static final String UTC = " UTC";
    private static final String OPEN_END = "end";
    private static final String MINUTES_OUT_OF_RANGE = "minutes must be from 1 to %d; got %d.";

    private WindowTexts() {
    }

    /** The minutes given, or the default; out of range is reported, never clamped. */
    static Optional<Long> minutes(WindowArguments given) {
        long minutes = given.minutes() == null ? DownloadWindow.DEFAULT_MINUTES : given.minutes();
        return minutes < 1 || minutes > DownloadWindow.MAX_WINDOW_MINUTES ? Optional.empty() : Optional.of(minutes);
    }

    static WindowResolution.Refused minutesOutOfRange(WindowArguments given) {
        return new WindowResolution.Refused(MINUTES_OUT_OF_RANGE.formatted(DownloadWindow.MAX_WINDOW_MINUTES, given.minutes()));
    }

    /** A time for a title: the clock, with the day when it is not the day the session ends on. */
    static String at(Instant instant, WindowSubject subject) {
        boolean sameDay = LocalDate.ofInstant(instant, ZoneOffset.UTC)
                .equals(LocalDate.ofInstant(subject.end(), ZoneOffset.UTC));
        return (sameDay ? CLOCK : DAY_AND_CLOCK).format(instant);
    }

    static String chunkSpan(Chunk chunk, WindowSubject subject) {
        String end = chunk.end() == null ? OPEN_END : at(chunk.end(), subject);
        return at(chunk.start(), subject) + "–" + end + UTC;
    }

    static Long chunkLengthMs(WindowSubject subject) {
        return subject.chunks().chunkLength().map(Duration::toMillis).orElse(null);
    }

    static ChosenWindow chunkChosen(DownloadWindow window, String evidence, Chunk chunk, Long median,
                                            WindowSubject subject) {
        return new ChosenWindow(window, evidence, chunk.file().id(), chunk.file().size(), median,
                chunkLengthMs(subject));
    }
}
