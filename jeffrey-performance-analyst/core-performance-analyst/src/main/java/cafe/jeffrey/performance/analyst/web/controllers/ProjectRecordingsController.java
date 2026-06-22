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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.performance.analyst.recordings.ProjectRecordingsManagerFactory;
import cafe.jeffrey.performance.analyst.recordings.RecordingResponses;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingResponse;

import java.util.List;
import java.util.Map;

/**
 * Lists the recordings downloaded into a given project (those persisted with the project's
 * {@code project_id}). Per-recording actions — file download and AI flamegraph prompts — continue to
 * use the global by-id endpoints, which serve recordings regardless of project scope.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/recordings")
public class ProjectRecordingsController {

    private final ProjectRecordingsManagerFactory managerFactory;
    private final RecordingProfileInfoProvider profileInfoProvider;

    public ProjectRecordingsController(
            ProjectRecordingsManagerFactory managerFactory,
            RecordingProfileInfoProvider profileInfoProvider) {
        this.managerFactory = managerFactory;
        this.profileInfoProvider = profileInfoProvider;
    }

    @GetMapping
    public List<RecordingResponse> list(@PathVariable("projectId") String projectId) {
        RecordingsCoreManager manager = managerFactory.forProject(projectId);
        List<Recording> recordings = manager.listRecordings();
        Map<String, List<RecordingTag>> tagsByRecording = manager.tagsForRecordings(
                recordings.stream().map(Recording::id).toList());
        return RecordingResponses.from(recordings, tagsByRecording, profileInfoProvider);
    }
}
