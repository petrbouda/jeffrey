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

package cafe.jeffrey.performance.analyst.recordings;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingTagsRepository;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;
import cafe.jeffrey.shared.ui.workspace.dto.RecordingResponse;

import java.util.List;
import java.util.Map;

/**
 * Lists the most-recent recordings across all projects for the analyst's global recordings view. Reads
 * through a project-agnostic {@link JdbcRecordingRepository#findLatestRecordings(int)} query rather than
 * the project-scoped {@code RecordingsCoreManager}, since the global view spans every project.
 */
public class RecentRecordingsManager {

    private final JdbcRecordingRepository recordingRepository;
    private final JdbcRecordingTagsRepository recordingTagsRepository;
    private final RecordingProfileInfoProvider profileInfoProvider;

    public RecentRecordingsManager(
            JdbcRecordingRepository recordingRepository,
            JdbcRecordingTagsRepository recordingTagsRepository,
            RecordingProfileInfoProvider profileInfoProvider) {
        this.recordingRepository = recordingRepository;
        this.recordingTagsRepository = recordingTagsRepository;
        this.profileInfoProvider = profileInfoProvider;
    }

    public List<RecordingResponse> latest(int limit) {
        List<Recording> recordings = recordingRepository.findLatestRecordings(limit);
        Map<String, List<RecordingTag>> tagsByRecording = recordingTagsRepository.listForRecordings(
                recordings.stream().map(Recording::id).toList());
        return RecordingResponses.from(recordings, tagsByRecording, profileInfoProvider);
    }
}
