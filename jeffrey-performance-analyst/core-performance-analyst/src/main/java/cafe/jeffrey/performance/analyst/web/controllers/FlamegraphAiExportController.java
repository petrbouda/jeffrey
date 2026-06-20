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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.performance.analyst.flamegraph.FlamegraphAiPrompt;
import cafe.jeffrey.performance.analyst.flamegraph.RecordingAiPromptManager;

import java.util.List;

/**
 * Returns the AI flamegraph prompts for a downloaded recording (one per sample event type), parsing the
 * recording's JFR file(s) in memory on first request and serving the cached markdown thereafter.
 */
@RestController
@RequestMapping("/api/internal/recordings")
public class FlamegraphAiExportController {

    private final RecordingAiPromptManager promptManager;

    public FlamegraphAiExportController(RecordingAiPromptManager promptManager) {
        this.promptManager = promptManager;
    }

    @PostMapping("/{recordingId}/ai-flamegraph-export")
    public List<FlamegraphAiPrompt> aiExport(@PathVariable("recordingId") String recordingId) {
        return promptManager.getPrompts(recordingId);
    }

    @GetMapping("/{recordingId}/ai-flamegraph-export")
    public List<FlamegraphAiPrompt> peek(@PathVariable("recordingId") String recordingId) {
        return promptManager.peekPrompts(recordingId);
    }
}
