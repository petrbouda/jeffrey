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

package cafe.jeffrey.otlpparser;

import cafe.jeffrey.provider.profile.api.RecordingSources;
import io.opentelemetry.proto.profiles.v1development.ProfilesData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.microscope.model.RecordingEventSource;

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

        RecordingInformation information = parser.provide(RecordingSources.of(file));

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

        RecordingInformation information = parser.provide(RecordingSources.of(file));

        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS), information.recordingStartedAt());
        assertEquals(Instant.ofEpochSecond(0, BASE_TIME_NANOS + 90_000_000_000L), information.recordingFinishedAt());
    }

    @Test
    void rejectsRecordingWithoutTimestamps() {
        Path file = tempDir.resolve("empty.otlp");
        OtlpTestFiles.writeFramed(file, List.of(ProfilesData.getDefaultInstance()));

        assertThrows(IllegalArgumentException.class, () -> parser.provide(RecordingSources.of(file)));
    }
}
