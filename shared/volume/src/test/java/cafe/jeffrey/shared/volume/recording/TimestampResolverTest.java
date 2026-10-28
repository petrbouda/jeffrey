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

package cafe.jeffrey.shared.volume.recording;

import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * When a file was opened — the timestamp a session is ordered by, so which chunk is still open,
 * which chunks a window covers and which files retention has aged all rest on it.
 */
class TimestampResolverTest {

    /** What the volume says about when a file was created — never what the name says. */
    private static final Instant VOLUME_CREATED_AT = Instant.parse("2026-03-01T08:00:00Z");

    private static VolumeEntry file(String name) {
        return new VolumeEntry(VolumePath.root().resolve(name), VolumeEntry.Kind.FILE, 1, Optional.of(VOLUME_CREATED_AT));
    }

    /** A listing row from a volume whose listings carry no timestamps. */
    private static VolumeEntry untimed(String name) {
        return new VolumeEntry(VolumePath.root().resolve(name), VolumeEntry.Kind.FILE, 1, Optional.empty());
    }

    @Nested
    class EachFormatSaysWhereItsTimestampComesFrom {

        @Test
        void aRecordingReadsItsOwnName() {
            assertSame(TimestampResolver.RECORDING_NAME, RecordingFormat.JFR.timestampResolver());
            assertSame(TimestampResolver.RECORDING_NAME, RecordingFormat.JFR_LZ4.timestampResolver());
        }
    }

    @Nested
    class FromTheName {

        @Test
        void readsTheInstantTheProfilerWroteIntoIt() {
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-120500.jfr")));
        }

        /**
         * async-profiler writes {@code %t} in the profiled machine's local time, so a JVM in Prague
         * names a chunk it opened at 12:05 UTC {@code 13:05} in winter. Read as UTC it would claim
         * to start an hour late, and every window and retention cut taken from it would be off by
         * that hour — so the provisioner writes the UTC offset after it, and the name is read at it.
         */
        @Test
        void readsTheNameAtTheOffsetItCarries() {
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-130500+0100.jfr")));
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-120500+0000.jfr")));
        }

        @Test
        void readsANegativeAndAHalfHourOffset() {
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-083500-0330.jfr")));
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-173500+0530.jfr")));
        }

        /** A name without an offset — a custom template — is read as UTC. */
        @Test
        void readsANameWithoutAnOffsetAsUtc() {
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-120500.jfr")));
        }

        @Test
        void readsTheOffsetFromTheArchiveToo() {
            assertEquals(
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-130500+0100.jfr")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-130500+0100.jfr.lz4")));
        }

        /**
         * The reason a JFR may be compressed at all. The archive is a new file whose creation
         * time is the moment the job ran — minutes or hours after the profiler opened the chunk,
         * and later than the chunk still open beside it. Read that way the archive looks like the
         * session's newest recording and takes the open chunk's place, at which point the
         * compression job compresses the file the profiler is writing and both retention jobs
         * consider it deletable.
         */
        @Test
        void isUnchangedByTheFileBeingCompressed() {
            assertEquals(
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-120500.jfr")),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-20260220-120500.jfr.lz4")));
        }
    }

    @Nested
    class WithoutATimeInTheListing {

        /** A chunk's own name is enough: the listing's missing time does not matter to it. */
        @Test
        void aRecordingStillReadsItsName() {
            assertEquals(
                    Optional.of(Instant.parse("2026-02-20T12:05:00Z")),
                    TimestampResolver.RECORDING_NAME.resolve(untimed("profile-20260220-120500.jfr")));
        }

        /** Nothing to go on: the caller takes one more look at the file rather than inventing a time. */
        @Test
        void anythingElseIsLeftForTheCallerToLookUp() {
            assertEquals(Optional.empty(), TimestampResolver.VOLUME.resolve(untimed("service-app.log")));
            assertEquals(Optional.empty(),
                    TimestampResolver.RECORDING_NAME.resolve(untimed("profile-not-a-timestamp.jfr")));
        }
    }

    @Nested
    class FallingBackToTheVolume {

        @Test
        void forARecordingNamedWithAnotherPrefix() {
            assertEquals(Optional.of(VOLUME_CREATED_AT),
                    TimestampResolver.RECORDING_NAME.resolve(file("recording-20260220-120500.jfr")));
        }

        /**
         * A name that does not parse must not raise: the caller drops a file it cannot describe,
         * so one oddly named file would leave the listing entirely rather than sort imprecisely.
         */
        @Test
        void forATimestampThatDoesNotParse() {
            assertEquals(Optional.of(VOLUME_CREATED_AT),
                    TimestampResolver.RECORDING_NAME.resolve(file("profile-not-a-timestamp.jfr")));
        }

        @Test
        void forAFileThatIsNotARecording() {
            assertEquals(Optional.of(VOLUME_CREATED_AT), TimestampResolver.VOLUME.resolve(file("service-app.log")));
        }
    }
}
