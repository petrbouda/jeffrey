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

package cafe.jeffrey.profile.recording;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Finds the JFR files a profile was built from, by recording id, so that auto-analysis can run the
 * JMC rules over them again on demand.
 *
 * <p>The recording's row already lists every file it was stored as, with the type it was
 * classified as on the way in, so this reads the row rather than the recordings directory: one
 * lookup by id instead of a listing of every file of every recording, and the classification the
 * import made instead of a second guess from the name. {@link RecordingStorageLayout} turns each
 * file into the path it sits at.
 *
 * <p>The rules:
 *
 * <ul>
 *   <li><b>Every JFR file, not the first.</b> A downloaded session is as many files as it rolled
 *       chunks. The import runs the rules over all of them, and a run on demand that took only one
 *       would replace findings about the whole run with findings about a few minutes of it.
 *   <li><b>JFR only.</b> The rule set reads JFR and nothing else. A heap dump, a pprof or OTLP
 *       import and a GC log beside the recording are not answers: returning one would report
 *       auto-analysis as available for a profile it can only fail on.
 *   <li><b>All of them or none.</b> The rules reason about the run as a whole, so a missing file
 *       does not cost a fraction of the findings -- it makes the rest describe a recording that was
 *       never taken. The import applies the same rule before it starts its own run.
 * </ul>
 *
 * <p>This is a lookup and only a lookup: it creates no directory and no file.
 */
public final class RecordingFileLookup {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingFileLookup.class);

    /**
     * The file types the JMC rule set can read.
     */
    private static final Set<ManagedFile> JFR_TYPES = Set.of(ManagedFile.JFR, ManagedFile.JFR_LZ4);

    private final RecordingRepository recordingRepository;
    private final Path recordingsDir;

    public RecordingFileLookup(RecordingRepository recordingRepository, Path recordingsDir) {
        if (recordingRepository == null) {
            throw new IllegalArgumentException("Recording repository is required");
        }
        if (recordingsDir == null) {
            throw new IllegalArgumentException("Recordings directory is required");
        }
        this.recordingRepository = recordingRepository;
        this.recordingsDir = recordingsDir;
    }

    /**
     * The JFR files of the given recording, in the order the repository lists them; empty when the
     * recording is unknown, has no JFR file, or is missing one of them on disk.
     *
     * @param recordingId the recording to look for; null or blank answers empty
     */
    public List<Path> findJfrFiles(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return List.of();
        }

        List<Path> jfrFiles = recordingRepository.findRecording(recordingId)
                .map(Recording::files)
                .orElse(List.of())
                .stream()
                .filter(file -> JFR_TYPES.contains(file.recordingFileType()))
                .map(this::storagePath)
                .toList();

        List<Path> missing = jfrFiles.stream()
                .filter(path -> !Files.isRegularFile(path))
                .toList();
        if (!missing.isEmpty()) {
            LOG.debug("Recording files are missing on disk: recording_id={} missing={}", recordingId, missing);
            return List.of();
        }
        return jfrFiles;
    }

    private Path storagePath(RecordingFile file) {
        return RecordingStorageLayout.storagePath(recordingsDir, file.recordingId(), file.filename());
    }
}
