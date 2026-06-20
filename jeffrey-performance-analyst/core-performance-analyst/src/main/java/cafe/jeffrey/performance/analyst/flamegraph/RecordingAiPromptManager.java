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
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the AI flamegraph prompts for a recording, caching the generated markdown on the filesystem.
 *
 * <p>The prompt is a deterministic function of the (immutable) JFR file plus a fixed threshold, so it is
 * a write-once cache: the first request parses the JFR and writes one {@code <slug>.md} per event type
 * under {@code <cacheBaseDir>/<recordingId>/}; later requests read those files back without re-parsing.
 * Files are kept human-readable (and the markdown carries its own {@code samples_total:} header, which we
 * read back for the UI chip).
 */
public class RecordingAiPromptManager {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingAiPromptManager.class);

    private static final Set<SupportedRecordingFile> JFR_FILE_TYPES =
            Set.of(SupportedRecordingFile.JFR, SupportedRecordingFile.JFR_LZ4);

    private static final Pattern SAMPLES_TOTAL = Pattern.compile("(?m)^samples_total:\\s*(\\d+)");

    private final RecordingsCoreManager recordingsManager;
    private final RecordingFlamegraphAiExporter exporter;
    private final Path cacheBaseDir;

    public RecordingAiPromptManager(
            RecordingsCoreManager recordingsManager,
            RecordingFlamegraphAiExporter exporter,
            Path cacheBaseDir) {
        this.recordingsManager = recordingsManager;
        this.exporter = exporter;
        this.cacheBaseDir = cacheBaseDir;
    }

    /**
     * Returns the AI prompts for the recording, generating and caching them on first request.
     */
    public List<FlamegraphAiPrompt> getPrompts(String recordingId) {
        Path cacheDir = cacheBaseDir.resolve(recordingId);

        List<FlamegraphAiPrompt> cached = readCache(cacheDir);
        if (!cached.isEmpty()) {
            LOG.info("Serving cached AI flamegraph prompts: recording_id={} prompts={}", recordingId, cached.size());
            return cached;
        }

        List<Path> jfrFiles = resolveJfrFiles(recordingId);
        LOG.info("Generating AI flamegraph prompts: recording_id={} jfr_files={}", recordingId, jfrFiles.size());
        List<FlamegraphAiPrompt> prompts = exporter.export(jfrFiles);
        writeCache(cacheDir, prompts);
        return prompts;
    }

    /**
     * Returns the already-cached prompts for the recording without parsing anything — an empty list if
     * nothing has been generated yet. Drives the recording row's "AI prompts ready" status.
     */
    public List<FlamegraphAiPrompt> peekPrompts(String recordingId) {
        return readCache(cacheBaseDir.resolve(recordingId));
    }

    private List<Path> resolveJfrFiles(String recordingId) {
        Recording recording = recordingsManager.listRecordings().stream()
                .filter(r -> r.id().equals(recordingId))
                .findFirst()
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

    private List<FlamegraphAiPrompt> readCache(Path cacheDir) {
        if (!Files.isDirectory(cacheDir)) {
            return List.of();
        }

        List<FlamegraphAiPrompt> prompts = new ArrayList<>();
        for (AiPromptType promptType : AiPromptType.values()) {
            Path file = cacheDir.resolve(promptType.fileName());
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                String markdown = Files.readString(file);
                prompts.add(new FlamegraphAiPrompt(
                        promptType.eventType().code(), promptType.label(), parseSamples(markdown), markdown));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read cached AI prompt: " + file, e);
            }
        }
        return prompts;
    }

    private void writeCache(Path cacheDir, List<FlamegraphAiPrompt> prompts) {
        try {
            Files.createDirectories(cacheDir);
            for (FlamegraphAiPrompt prompt : prompts) {
                Optional<AiPromptType> promptType = AiPromptType.byEventCode(prompt.eventType());
                if (promptType.isEmpty()) {
                    continue;
                }
                Files.writeString(cacheDir.resolve(promptType.get().fileName()), prompt.markdown());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to cache AI prompts: " + cacheDir, e);
        }
    }

    private static long parseSamples(String markdown) {
        Matcher matcher = SAMPLES_TOTAL.matcher(markdown);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }
}
