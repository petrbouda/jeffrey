/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.platform.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.exception.Exceptions;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.profile.parser.chunk.Recordings;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RecordingsDownloadManagerImpl implements RecordingsDownloadManager {

    private record RepositoryFiles(List<RepositoryFile> recordings, List<RepositoryFile> additionalFiles) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsDownloadManagerImpl.class);

    private static final List<SupportedRecordingFile> SUPPORTED_RECORDING_FILES = List.of(
            SupportedRecordingFile.JFR,
            SupportedRecordingFile.JFR_LZ4);

    private static final SupportedRecordingFile TARGET_RECORDING_FILE = SupportedRecordingFile.JFR_LZ4;

    private final ProjectInfo projectInfo;
    private final ProjectRecordingInitializer recordingInitializer;
    private final RepositoryManager repositoryManager;
    private final Consumer<String> repositoryCompressionTrigger;


    public RecordingsDownloadManagerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingInitializer recordingInitializer,
            RepositoryManager repositoryManager,
            Consumer<String> repositoryCompressionTrigger) {

        this.projectInfo = projectInfo;
        this.recordingInitializer = recordingInitializer;
        this.repositoryManager = repositoryManager;
        this.repositoryCompressionTrigger = repositoryCompressionTrigger;
    }

    @Override
    public void mergeAndDownloadSession(String sessionId) {
        RecordingSession session = findRecordingSession(sessionId);

        // Merge and download all recordings in the session at this time
        mergeAndDownloadSelectedRecordings(sessionId, session.files());
    }

    @Override
    public void mergeAndDownloadRecordings(String sessionId, List<String> recordingIds) {
        // Merge and download only the selected recordings
        mergeAndDownloadSelectedRecordings(sessionId, recordings(sessionId, recordingIds));
    }

    private void mergeAndDownloadSelectedRecordings(String sessionId, List<RepositoryFile> recordings) {
        RepositoryFiles files = splitRepositoryFiles(recordings);
        boolean allCompressed = allFilesCompressed(files.recordings);

        List<RepositoryFile> finalRecordings = files.recordings;

        // If not all files are compressed, trigger compression and re-check
        // if still not compressed, throw an error
        if (!allCompressed) {
            // Trigger repository compression to compress uncompressed files for this session
            repositoryCompressionTrigger.accept(sessionId);

            List<String> recordingIds = recordings.stream()
                    .map(RepositoryFile::id)
                    .toList();

            List<RepositoryFile> newRecordings = recordings(sessionId, recordingIds);
            boolean newCompressed = allFilesCompressed(newRecordings);
            if (newCompressed) {
                finalRecordings = newRecordings;
            } else {
                LOG.warn("Not all recording files were compressed after repository compression trigger: " +
                        "session_id={} recording_ids={}", sessionId, recordingIds);
                throw Exceptions.compressionError("Cannot merge and download recordings, not all files are compressed");
            }
        }

        createNewRecording(sessionId, finalRecordings);
    }

    private List<RepositoryFile> recordings(String sessionId, List<String> recordingIds) {
        RecordingSession session = findRecordingSession(sessionId);
        return session.files().stream()
                .filter(file -> recordingIds.contains(file.id()))
                .filter(RepositoryFile::isRecordingFile)
                .toList();
    }

    /**
     * Splits the repository files into recording files and additional files.
     *
     * @param repositoryFiles the list of repository files to split
     * @return a grouped repository files
     */
    private RepositoryFiles splitRepositoryFiles(List<RepositoryFile> repositoryFiles) {
        Map<Boolean, List<RepositoryFile>> partitioned = repositoryFiles.stream()
                .collect(Collectors.partitioningBy(file -> SUPPORTED_RECORDING_FILES.contains(file.fileType())));

        return new RepositoryFiles(partitioned.get(true), partitioned.get(false));
    }

    /**
     * Checks if all recording files are compressed (JFR_LZ4).
     *
     * @param recordingFiles the list of recording files to check
     * @return true if all files are compressed, false otherwise
     */
    private boolean allFilesCompressed(List<RepositoryFile> recordingFiles) {
        return recordingFiles.stream()
                .allMatch(file -> file.fileType() == TARGET_RECORDING_FILE);
    }

    @Override
    public void createNewRecording(String recordingName, List<RepositoryFile> repositoryFiles) {
        List<RepositoryFile> recordingFiles = repositoryFiles.stream()
                .filter(RepositoryFile::isRecordingFile)
                .toList();

        if (recordingFiles.isEmpty()) {
            throw new IllegalArgumentException("No recording files selected to merge and upload: " + recordingName);
        }

        // Resolve the filename of the merged recordings using the
        // recording name with the extension of the recording file
        RepositoryFile firstRecordingFile = recordingFiles.getFirst();
        String recordingFilename = firstRecordingFile.fileType()
                .appendExtension("profile");

        List<RepositoryFile> additionalFiles = repositoryFiles.stream()
                .filter(RepositoryFile::isArtifactFile)
                .toList();

        NewRecording newRecording = new NewRecording(recordingName, recordingFilename, null);
        try (NewRecordingHolder holder = recordingInitializer.newRecording(newRecording, additionalFiles)) {
            List<Path> recordingPaths = recordingFiles.stream()
                    .filter(file -> file.status() == RecordingStatus.FINISHED)
                    .map(RepositoryFile::filePath)
                    .toList();

            // Try streaming merge (FileChannel.transferTo) first for better performance,
            // fallback to file copy if it fails (e.g., on Azure blob storage)
            try {
                Recordings.mergeByStreaming(recordingPaths, holder.outputPath());
            } catch (Exception e) {
                LOG.warn("Streaming merge failed, falling back to file copy: {}", e.getMessage());
                // Delete partially written file before fallback (mergeByFileCopy uses APPEND)
                Files.deleteIfExists(holder.outputPath());
                Recordings.mergeByFileCopy(recordingPaths, holder.outputPath());
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot upload the recording: " + newRecording, e);
        }

        LOG.info("Merged and Uploaded recording: name={} folder_id={} project_id={}",
                newRecording.recordingName(), newRecording.folderId(), projectInfo.id());
    }

    private RecordingSession findRecordingSession(String sessionId) {
        return repositoryManager.findRecordingSessions(sessionId)
                .orElseThrow(() -> new RuntimeException("Recording session not found: " + sessionId));
    }
}
