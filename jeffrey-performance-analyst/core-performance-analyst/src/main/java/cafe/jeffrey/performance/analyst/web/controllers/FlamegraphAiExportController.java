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

package cafe.jeffrey.performance.analyst.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingFlamegraphAiExporter;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Generates AI flamegraph prompts for a downloaded recording by parsing its JFR file(s) in memory.
 * The prompts are printed to STDOUT and also returned as markdown so the UI can confirm.
 */
@RestController
@RequestMapping("/api/internal/recordings")
public class FlamegraphAiExportController {

    private static final Logger LOG = LoggerFactory.getLogger(FlamegraphAiExportController.class);

    private static final Set<SupportedRecordingFile> JFR_FILE_TYPES =
            Set.of(SupportedRecordingFile.JFR, SupportedRecordingFile.JFR_LZ4);

    private static final String PROMPT_SEPARATOR = "\n\n---\n\n";

    private final RecordingsCoreManager recordingsManager;
    private final RecordingFlamegraphAiExporter exporter;

    public FlamegraphAiExportController(
            RecordingsCoreManager recordingsManager,
            RecordingFlamegraphAiExporter exporter) {
        this.recordingsManager = recordingsManager;
        this.exporter = exporter;
    }

    @PostMapping(value = "/{recordingId}/ai-flamegraph-export", produces = "text/markdown")
    public String aiExport(@PathVariable("recordingId") String recordingId) {
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

        LOG.info("Generating AI flamegraph prompts: recording_id={} jfr_files={}", recordingId, jfrFiles.size());
        List<String> prompts = exporter.export(jfrFiles);
        return String.join(PROMPT_SEPARATOR, prompts);
    }
}
