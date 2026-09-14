/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.shared.common.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * A recording as Microscope stores it: the files a profile is parsed from, in order, and the
 * files that came beside them. An upload has one recording file; a session downloaded from a hub
 * has one per JFR chunk, kept as the hub served them rather than joined into one, because the
 * parser reads them chunk by chunk anyway. {@link #recordingFiles()} is the one reading of
 * "the recording" a consumer should take; {@link #files()} is everything the recording holds.
 */
public record Recording(
        String id,
        String recordingName,
        String projectId,
        String groupId,
        RecordingEventSource eventSource,
        Instant createdAt,
        Instant recordingStartedAt,
        Instant recordingFinishedAt,
        boolean hasProfile,
        String profileId,
        String profileName,
        List<RecordingFile> files) {

    public Duration recordingDuration() {
        if (recordingStartedAt == null || recordingFinishedAt == null) {
            return Duration.ZERO;
        }
        return Duration.between(recordingStartedAt, recordingFinishedAt);
    }

    /**
     * The order the recording files are read in: by name with the chunk extension stripped, which
     * for the rotated chunks of a session is the order they were written in — a chunk's name
     * carries its timestamp, and the hub's own listing is dated the same way — and which does not
     * change when the hub compresses a chunk and renames it.
     */
    private static final Comparator<RecordingFile> RECORDING_FILE_ORDER =
            Comparator.comparing(RecordingFile::nameWithoutChunkExtension);

    /**
     * The files the profile is parsed from, in reading order: one for an uploaded recording, one
     * per chunk for a session downloaded from a hub. Empty for a heap dump, which is not parsed
     * as a recording at all.
     */
    public List<RecordingFile> recordingFiles() {
        return files.stream()
                .filter(RecordingFile::isRecordingFile)
                .sorted(RECORDING_FILE_ORDER)
                .toList();
    }

    /**
     * Everything the recording holds beside its recording files: heap dumps, logs, perf counters.
     */
    public List<RecordingFile> additionalFiles() {
        return files.stream()
                .filter(file -> !file.isRecordingFile())
                .toList();
    }

    /**
     * The size of what a profile is parsed from — every recording file together — or, for a
     * recording with none (a heap dump), of its first file.
     */
    public long recordingSizeInBytes() {
        List<RecordingFile> recordingFiles = recordingFiles();
        if (recordingFiles.isEmpty()) {
            return files.isEmpty() ? 0 : files.getFirst().sizeInBytes();
        }
        return recordingFiles.stream().mapToLong(RecordingFile::sizeInBytes).sum();
    }

    public Recording withFiles(List<RecordingFile> files) {
        return new Recording(
                id, recordingName, projectId, groupId, eventSource, createdAt,
                recordingStartedAt, recordingFinishedAt, hasProfile, profileId, profileName,
                List.copyOf(files));
    }
}
