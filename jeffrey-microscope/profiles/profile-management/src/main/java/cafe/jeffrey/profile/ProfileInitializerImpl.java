/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.manager.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.ProfileInfoRepository;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DataSourceUtils;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.GroupLabel;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager databaseManager;
    private final RecordingEventParser recordingEventParser;
    private final EventWriter.Factory eventWriterFactory;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileDataInitializer profileDataInitializer;
    private final Clock clock;

    public ProfileInitializerImpl(
            ProfileRepositories profileRepositories,
            DatabaseManager databaseManager,
            RecordingEventParser recordingEventParser,
            EventWriter.Factory eventWriterFactory,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            Clock clock) {

        this.profileRepositories = profileRepositories;
        this.databaseManager = databaseManager;
        this.recordingEventParser = recordingEventParser;
        this.eventWriterFactory = eventWriterFactory;
        this.profileManagerFactory = profileManagerFactory;
        this.profileDataInitializer = profileDataInitializer;
        this.clock = clock;
    }

    @Override
    public ProfileManager initialize(ProfileInfo profileInfo, String recordingId, Path recordingPath) {
        LOG.debug("Initializing profile: profileId={} recordingId={}", profileInfo.id(), recordingId);
        Instant startedAt = clock.instant();

        // Open database connection for the new profile
        // it's creates a new database file on disk if it does not exist yet
        DataSource dataSource = databaseManager.open(profileInfo.id());
        try {
            // Store profile context (workspace_id, project_id) in the profile database
            // Skip for Recordings profiles where workspace/project are null
            if (profileInfo.projectId() != null && profileInfo.workspaceId() != null) {
                ProfileInfoRepository profileInfoRepository = profileRepositories.newProfileInfoRepository(dataSource);
                profileInfoRepository.insert(new ProfileInfoRepository.ProfileContext(
                        profileInfo.id(),
                        profileInfo.projectId(),
                        profileInfo.workspaceId()));
            }

            // Parse recording and store events into the database
            EventWriter eventWriter = eventWriterFactory.create(dataSource);
            recordingEventParser.start(eventWriter, recordingPath);
            eventWriter.onComplete();

            ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

            // Initialize profile data (Event Viewer, Thread Viewer, Guardian, ...)
            // initializer works with already inserted events in the database
            // so it must be done after parsing is complete.
            // It's mainly used to pre-generate various views and analyses and use caching for faster access later.
            profileDataInitializer.initialize(profileManager);

            // Process additional files (like logs, metrics, heap-dumps, perf-counters etc.)
            // Currently only perf-counters are supported
            // Skip for Recordings where recordingId is null
            if (recordingId != null) {
                AdditionalFilesManager additionalFilesManager = profileManager.additionalFilesManager();
                additionalFilesManager.processAdditionalFiles(recordingId);
            }

            // Ensure all data is flushed to disk - especially important for WAL mode databases
            // WAL checkpointing merges the WAL (Write-Ahead Log) into the main database file
            profileRepositories.databaseClientProvider(dataSource)
                    .provide(GroupLabel.INFRASTRUCTURE)
                    .walCheckpoint();

            long elapsedMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli();
            LOG.info("Profile parsed and initialized: profile_id={} profile_name={} elapsed_ms={}",
                    profileInfo.id(), profileInfo.name(), elapsedMs);

            return profileManager;
        } finally {
            DataSourceUtils.close(dataSource);
        }
    }
}
