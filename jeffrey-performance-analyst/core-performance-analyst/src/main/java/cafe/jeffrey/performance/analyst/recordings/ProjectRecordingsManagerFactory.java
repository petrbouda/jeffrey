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

import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingRepository;
import cafe.jeffrey.performance.analyst.persistence.JdbcRecordingTagsRepository;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;
import cafe.jeffrey.recordings.core.manager.RecordingProfileCleanup;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.nio.file.Path;
import java.time.Clock;

/**
 * Builds a {@link RecordingsCoreManager} bound to a single project scope. The analyst's recording
 * repository fixes its {@code project_id} filter at construction time, so a project-scoped manager is
 * needed both to persist a downloaded recording under a project and to list that project's recordings.
 *
 * <p>{@code forProject(null)} yields the unscoped manager (recordings with {@code project_id IS NULL}),
 * which keeps a single construction path for the global and project-scoped managers alike.</p>
 */
public class ProjectRecordingsManagerFactory {

    private final Clock clock;
    private final Path recordingsDir;
    private final DatabaseClientProvider databaseClientProvider;

    public ProjectRecordingsManagerFactory(
            Clock clock, Path recordingsDir, DatabaseClientProvider databaseClientProvider) {
        this.clock = clock;
        this.recordingsDir = recordingsDir;
        this.databaseClientProvider = databaseClientProvider;
    }

    public RecordingsCoreManager forProject(String projectId) {
        return new RecordingsCoreManagerImpl(
                clock,
                recordingsDir,
                new JdbcRecordingRepository(databaseClientProvider, projectId, clock),
                new JdbcRecordingTagsRepository(databaseClientProvider),
                RecordingMetadataParser.NOOP,
                RecordingProfileCleanup.NOOP);
    }
}
