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
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds the recording file a profile was built from, by recording id.
 *
 * <p>Recordings are stored flat, each file named {@code <recordingId>-<name>}: the prefix keeps the
 * one shared directory unique across recordings and is not part of the name the file is known by.
 * {@code RecordingsCoreManagerImpl.storagePath} in {@code recordings-core} is what writes that
 * convention; this class is what reads it back. The two cannot see each other — neither module
 * depends on the other — so they have to agree by hand, and a disagreement of one prefix would
 * leave every recording unfindable.
 *
 * <p>Kept out of the {@code @Bean} factory that uses it because the two rules below are the part
 * worth testing, and a rule that lives in a configuration lambda is a rule that gets tested through
 * a Spring context or not at all.
 *
 * <p>The rules:
 *
 * <ul>
 *   <li><b>The prefix ends at the separator.</b> Matching on {@code recordingId} alone would let
 *       {@code rec-1} claim {@code rec-10}'s files, so the match is on {@code recordingId + "-"}.
 *   <li><b>A recording beats an artifact.</b> One recording can have several files — a heap dump, a
 *       GC log, perf counters — and auto-analysis wants the JFR, not the log that sits beside it.
 *       Where no file is a recording the first artifact is still returned: a heap-dump profile has
 *       nothing else to offer, and answering with nothing would take the feature away from it
 *       rather than give it a better answer.
 * </ul>
 *
 * <p>This is a lookup and only a lookup. It creates no directory and no file, so asking about a
 * recording that does not exist leaves the disk exactly as it was.
 */
public final class RecordingFileLookup {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingFileLookup.class);

    /**
     * Separates the storage prefix from the filename. Must agree with the writer in
     * {@code recordings-core}; see the class javadoc.
     */
    private static final String RECORDING_ID_SEPARATOR = "-";

    private final Path recordingsDir;

    public RecordingFileLookup(Path recordingsDir) {
        if (recordingsDir == null) {
            throw new IllegalArgumentException("Recordings directory is required");
        }
        this.recordingsDir = recordingsDir;
    }

    /**
     * The file the given recording was stored as, or empty when the recording has no file on disk.
     *
     * @param recordingId the recording to look for; null or blank answers empty
     */
    public Optional<Path> find(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return Optional.empty();
        }
        if (!Files.isDirectory(recordingsDir)) {
            return Optional.empty();
        }

        List<Path> candidates = candidatesFor(recordingId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(RecordingFileLookup::isRecording)
                .findFirst()
                .or(() -> Optional.of(candidates.getFirst()));
    }

    /**
     * Every file belonging to the recording, sorted by name so that the pick does not depend on the
     * order the filesystem happens to hand the directory back in.
     */
    private List<Path> candidatesFor(String recordingId) {
        String prefix = recordingId + RECORDING_ID_SEPARATOR;
        try (Stream<Path> stream = Files.list(recordingsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOG.warn("Could not list the recordings directory: directory={} recording_id={} error={}",
                    recordingsDir, recordingId, e.getMessage());
            return List.of();
        }
    }

    private static boolean isRecording(Path path) {
        return ManagedFile.of(path).fileCategory() == FileCategory.RECORDING;
    }
}
