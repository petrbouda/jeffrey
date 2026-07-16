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

package cafe.jeffrey.pprofparser;

import com.google.perftools.profiles.ProfileProto.Profile;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.shared.common.model.RecordingEventSource;

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

    @Override
    public RecordingInformation provide(Path recordingPath) {
        Profile profile = streamReader.read(recordingPath);
        Instant startedAt = Instant.ofEpochSecond(0, profile.getTimeNanos());
        Instant finishedAt = startedAt.plusNanos(profile.getDurationNanos());
        return new RecordingInformation(sizeInBytes(recordingPath), RecordingEventSource.PPROF, startedAt, finishedAt);
    }

    private static long sizeInBytes(Path recordingPath) {
        try {
            return Files.size(recordingPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read pprof recording size: " + recordingPath, e);
        }
    }
}
