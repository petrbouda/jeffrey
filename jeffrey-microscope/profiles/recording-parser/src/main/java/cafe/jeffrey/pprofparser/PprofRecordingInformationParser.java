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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Extracts the upload-time metadata of a pprof recording. pprof carries a single collection
 * timestamp ({@code time_nanos}) and an optional {@code duration_nanos}; the recording window is
 * {@code [time_nanos, time_nanos + duration_nanos]}. Since pprof has no per-sample timestamps, this
 * is the finest window Jeffrey can report for the whole profile.
 */
public class PprofRecordingInformationParser implements RecordingInformationParser {

    private final PprofStreamReader streamReader;

    public PprofRecordingInformationParser() {
        this.streamReader = new PprofStreamReader();
    }

    /**
     * The earliest collection timestamp and the latest window end across the files, with their
     * sizes added up — so several rotated profiles describe one recording rather than whichever
     * of them happened to be read.
     */
    @Override
    public RecordingInformation provide(RecordingSources sources) {
        Instant startedAt = null;
        Instant finishedAt = null;
        long sizeInBytes = 0;

        for (Path recordingPath : sources.files()) {
            Profile profile = streamReader.read(recordingPath);
            Instant fileStartedAt = Instant.ofEpochSecond(0, profile.getTimeNanos());
            Instant fileFinishedAt = fileStartedAt.plusNanos(profile.getDurationNanos());

            if (startedAt == null || fileStartedAt.isBefore(startedAt)) {
                startedAt = fileStartedAt;
            }
            if (finishedAt == null || fileFinishedAt.isAfter(finishedAt)) {
                finishedAt = fileFinishedAt;
            }
            sizeInBytes += sizeInBytes(recordingPath);
        }

        return new RecordingInformation(sizeInBytes, RecordingEventSource.PPROF, startedAt, finishedAt);
    }

    private static long sizeInBytes(Path recordingPath) {
        try {
            return Files.size(recordingPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read pprof recording size: " + recordingPath, e);
        }
    }
}
