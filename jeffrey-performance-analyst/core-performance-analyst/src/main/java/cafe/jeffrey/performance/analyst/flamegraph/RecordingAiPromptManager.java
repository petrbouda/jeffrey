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

package cafe.jeffrey.performance.analyst.flamegraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.performance.analyst.persistence.GeneratedPrompt;
import cafe.jeffrey.performance.analyst.persistence.GeneratedPromptRepository;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the AI flamegraph prompts for a recording, caching the generated markdown in the SQLite
 * store (the {@code generated_prompts} table) instead of on the filesystem.
 *
 * <p>The prompt is a deterministic function of the (immutable) JFR file plus a fixed threshold, so it
 * is a write-once cache: the first request parses the JFR and upserts one row per event type; later
 * requests read those rows back without re-parsing.
 */
public class RecordingAiPromptManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingAiPromptManager.class);

    private static final Set<SupportedRecordingFile> JFR_FILE_TYPES =
            Set.of(SupportedRecordingFile.JFR, SupportedRecordingFile.JFR_LZ4);

    private final RecordingsCoreManager recordingsManager;
    private final RecordingFlamegraphAiExporter exporter;
    private final GeneratedPromptRepository promptRepository;
    private final Clock clock;

    public RecordingAiPromptManager(
            RecordingsCoreManager recordingsManager,
            RecordingFlamegraphAiExporter exporter,
            GeneratedPromptRepository promptRepository,
            Clock clock) {
        this.recordingsManager = recordingsManager;
        this.exporter = exporter;
        this.promptRepository = promptRepository;
        this.clock = clock;
    }

    /**
     * Returns the AI prompts for the recording, generating and persisting them on first request.
     */
    public List<FlamegraphAiPrompt> getPrompts(String recordingId) {
        List<GeneratedPrompt> cached = promptRepository.findByRecording(recordingId);
        if (!cached.isEmpty()) {
            LOG.info("Serving cached AI flamegraph prompts: recording_id={} prompts={}", recordingId, cached.size());
            return toFlamegraphPrompts(cached);
        }

        List<Path> jfrFiles = resolveJfrFiles(recordingId);
        LOG.info("Generating AI flamegraph prompts: recording_id={} jfr_files={}", recordingId, jfrFiles.size());
        List<FlamegraphAiPrompt> prompts = exporter.export(jfrFiles);
        for (FlamegraphAiPrompt prompt : prompts) {
            promptRepository.upsert(new GeneratedPrompt(
                    recordingId, prompt.eventType(), prompt.label(), prompt.samples(), prompt.markdown(), clock.instant()));
        }
        return prompts;
    }

    /**
     * Returns the already-cached prompts for the recording without parsing anything — an empty list if
     * nothing has been generated yet.
     */
    public List<FlamegraphAiPrompt> peekPrompts(String recordingId) {
        return toFlamegraphPrompts(promptRepository.findByRecording(recordingId));
    }

    private static List<FlamegraphAiPrompt> toFlamegraphPrompts(List<GeneratedPrompt> stored) {
        return stored.stream()
                .map(p -> new FlamegraphAiPrompt(p.eventType(), p.label(), p.samples(), p.markdown()))
                .toList();
    }

    private List<Path> resolveJfrFiles(String recordingId) {
        Recording recording = recordingsManager.findRecording(recordingId)
                .orElseThrow(() -> Exceptions.invalidRequest("Recording not found: " + recordingId));

        List<Path> jfrFiles = recording.files().stream()
                .filter(file -> JFR_FILE_TYPES.contains(file.recordingFileType()))
                .map(file -> recordingsManager.findRecordingFile(recordingId, file.id()))
                .flatMap(Optional::stream)
                .toList();

        if (jfrFiles.isEmpty()) {
            throw Exceptions.invalidRequest("Recording has no JFR files: " + recordingId);
        }
        return jfrFiles;
    }
}
