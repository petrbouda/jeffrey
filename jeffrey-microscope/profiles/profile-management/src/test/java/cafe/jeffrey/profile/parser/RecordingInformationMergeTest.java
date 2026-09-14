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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The information of a recording held as several chunks is the chunks' information merged: the
 * window spans them, the sizes add up, and a chunk that cannot say when it ran does not shrink
 * the window.
 */
class RecordingInformationMergeTest {

    private static final Instant T0 = Instant.parse("2026-03-01T12:00:00Z");

    private static RecordingInformation info(long size, Instant start, Instant end) {
        return new RecordingInformation(size, RecordingEventSource.ASYNC_PROFILER, start, end);
    }

    @Test
    void spansTheChunksAndAddsUpTheirSizes() {
        RecordingInformation merged = RecordingInformation.merge(List.of(
                info(10, T0, T0.plusSeconds(60)),
                info(20, T0.plusSeconds(60), T0.plusSeconds(120)),
                info(30, T0.plusSeconds(120), T0.plusSeconds(150))));

        assertEquals(60, merged.sizeInBytes());
        assertEquals(RecordingEventSource.ASYNC_PROFILER, merged.eventSource());
        assertEquals(T0, merged.recordingStartedAt());
        assertEquals(T0.plusSeconds(150), merged.recordingFinishedAt());
    }

    @Test
    void aChunkWithoutAWindowDoesNotShrinkTheWindow() {
        RecordingInformation merged = RecordingInformation.merge(List.of(
                info(10, null, null),
                info(20, T0, T0.plusSeconds(60))));

        assertEquals(T0, merged.recordingStartedAt());
        assertEquals(T0.plusSeconds(60), merged.recordingFinishedAt());

        RecordingInformation unknown = RecordingInformation.merge(List.of(info(10, null, null)));
        assertNull(unknown.recordingStartedAt());
        assertNull(unknown.recordingFinishedAt());
    }

    @Test
    void refusesNothingToMerge() {
        assertThrows(IllegalArgumentException.class, () -> RecordingInformation.merge(List.of()));
    }

    /**
     * A parser answering per file answers for a set of files through the same door, in the
     * order given.
     */
    @Test
    void aParserProvidesForSeveralFilesByMergingWhatItProvidesForEach() {
        Map<Path, RecordingInformation> perFile = Map.of(
                Path.of("profile-1.jfr"), info(10, T0, T0.plusSeconds(60)),
                Path.of("profile-2.jfr.lz4"), info(20, T0.plusSeconds(60), T0.plusSeconds(120)));
        RecordingInformationParser parser = perFile::get;

        RecordingInformation merged = parser.provide(List.of(Path.of("profile-1.jfr"), Path.of("profile-2.jfr.lz4")));

        assertEquals(30, merged.sizeInBytes());
        assertEquals(T0, merged.recordingStartedAt());
        assertEquals(T0.plusSeconds(120), merged.recordingFinishedAt());
    }
}
