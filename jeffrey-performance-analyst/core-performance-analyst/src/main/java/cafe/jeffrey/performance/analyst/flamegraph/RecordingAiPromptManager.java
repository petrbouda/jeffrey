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
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettings;
import cafe.jeffrey.performance.analyst.settings.ProjectAiSettingsResolver;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the AI flamegraph prompts for a recording, caching the generated markdown and the frame
 * index behind it in the SQLite store (the {@code generated_prompts} table).
 *
 * <p>The prompt is a deterministic function of the (immutable) JFR file plus the project's prune
 * threshold, so it is a write-once cache: the first request parses the JFR and upserts one row per
 * profile; later requests read those rows back without re-parsing.</p>
 */
public class RecordingAiPromptManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingAiPromptManager.class);

    private static final Set<SupportedRecordingFile> JFR_FILE_TYPES =
            Set.of(SupportedRecordingFile.JFR, SupportedRecordingFile.JFR_LZ4);

    private final RecordingsCoreManager recordingsManager;
    private final RecordingFlamegraphAiExporter exporter;
    private final GeneratedPromptRepository promptRepository;
    private final ProjectAiSettingsResolver settingsResolver;
    private final Clock clock;

    public RecordingAiPromptManager(
            RecordingsCoreManager recordingsManager,
            RecordingFlamegraphAiExporter exporter,
            GeneratedPromptRepository promptRepository,
            ProjectAiSettingsResolver settingsResolver,
            Clock clock) {
        this.recordingsManager = recordingsManager;
        this.exporter = exporter;
        this.promptRepository = promptRepository;
        this.settingsResolver = settingsResolver;
        this.clock = clock;
    }

    /**
     * Returns the AI prompts for the recording, generating and persisting them on first request.
     * {@code projectId} selects the prune threshold; it may be null for an unscoped recording, in which
     * case the built-in default applies.
     */
    public List<FlamegraphAiPrompt> getPrompts(String recordingId, String projectId) {
        List<GeneratedPrompt> cached = promptRepository.findByRecording(recordingId);
        if (!cached.isEmpty()) {
            LOG.info("Serving cached AI flamegraph prompts: recording_id={} prompts={}", recordingId, cached.size());
            return toFlamegraphPrompts(cached);
        }

        ProjectAiSettings settings = settingsResolver.resolve(projectId);
        List<Path> jfrFiles = resolveJfrFiles(recordingId);
        LOG.info("Generating AI flamegraph prompts: recording_id={} jfr_files={} prune_threshold_pct={}",
                recordingId, jfrFiles.size(), settings.pruneThresholdPct());

        List<FlamegraphAiPrompt> prompts = exporter.export(jfrFiles, settings.pruneThresholdPct());
        for (FlamegraphAiPrompt prompt : prompts) {
            promptRepository.upsert(new GeneratedPrompt(
                    recordingId,
                    prompt.eventType(),
                    prompt.label(),
                    prompt.samples(),
                    prompt.markdown(),
                    Json.toString(prompt.frameIndex()),
                    clock.instant()));
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
                .map(p -> new FlamegraphAiPrompt(
                        p.eventType(), p.label(), p.samples(), p.markdown(), readFrameIndex(p)))
                .toList();
    }

    /**
     * Prompts cached before the frame index existed have no JSON to read. They stay usable for reading,
     * but any claim against them will be reported as ungrounded rather than silently trusted.
     */
    private static ProfileFrameIndex readFrameIndex(GeneratedPrompt prompt) {
        String json = prompt.frameIndexJson();
        if (json == null || json.isBlank()) {
            return ProfileFrameIndex.empty();
        }
        try {
            return Json.read(json, ProfileFrameIndex.class);
        } catch (RuntimeException e) {
            LOG.warn("Could not read the cached frame index: recording_id={} event_type={} message={}",
                    prompt.recordingId(), prompt.eventType(), e.getMessage());
            return ProfileFrameIndex.empty();
        }
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
