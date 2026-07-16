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

package cafe.jeffrey.otlpparser;

import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtlpRecordingInformationParserTest {

    private static final long BASE_TIME_NANOS = 1_752_000_000_000_000_000L;
    private static final long DURATION_NANOS = 60_000_000_000L;

    @TempDir
    Path tempDir;

    private final OtlpRecordingInformationParser parser = new OtlpRecordingInformationParser();

    @Test
    void derivesTimeRangeFromProfileTimesAndDurations() {
        OtlpTestFixtures fixtures = new OtlpTestFixtures();
        fixtures.profile(fixtures.profileBuilder("cpu", "nanoseconds", BASE_TIME_NANOS)
                .setDurationNano(DURATION_NANOS)
                .build());
        fixtures.profile(fixtures.profileBuilder("alloc", "bytes", BASE_TIME_NANOS - 5_000_000_000L)
                .build());

        Path file = tempDir.resolve("recording.otlp");
        OtlpTestFiles.writeFramed(file, List.of(fixtures.build()));

        RecordingInformation information = parser.provide(file);

        assertEquals(RecordingEventSource.OPEN_TELEMETRY, information.eventSource());
        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS - 5_000_000_000L), information.recordingStartedAt());
        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS + DURATION_NANOS), information.recordingFinishedAt());
        assertTrue(information.sizeInBytes() > 0);
    }

    @Test
    void includesSampleTimestampsInTheRange() {
        OtlpTestFixtures fixtures = new OtlpTestFixtures();
        int function = fixtures.function("com.example.Foo.run");
        int location = fixtures.location(0, function, 1, 0);
        int stack = fixtures.stack(List.of(location));

        fixtures.profile(fixtures.profileBuilder("cpu", "nanoseconds", BASE_TIME_NANOS)
                .addSamples(fixtures.sampleBuilder(stack)
                        .addValues(1)
                        .addTimestampsUnixNano(BASE_TIME_NANOS + 90_000_000_000L))
                .build());

        Path file = tempDir.resolve("recording.otlp");
        OtlpTestFiles.writeFramed(file, List.of(fixtures.build()));

        RecordingInformation information = parser.provide(file);

        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS), information.recordingStartedAt());
        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS + 90_000_000_000L), information.recordingFinishedAt());
    }

    @Test
    void rejectsRecordingWithoutTimestamps() {
        Path file = tempDir.resolve("empty.otlp");
        OtlpTestFiles.writeFramed(file, List.of(ProfilesData.getDefaultInstance()));

        assertThrows(IllegalArgumentException.class, () -> parser.provide(file));
    }
}
