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
import cafe.jeffrey.profile.manager.additional.AdditionalFilesManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingFormatRegistry;
import cafe.jeffrey.provider.profile.api.ProfileInfoRepository;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.common.span.Spans;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    private static final String EVENTS_TABLE = "events";

    /**
     * Clustering keys of the events table: queries always filter by event type and very often by a
     * relative time range, so ordering row groups by (event_type, time) gives both predicates tight
     * zone maps.
     */
    private static final List<String> EVENTS_CLUSTERING_COLUMNS =
            List.of("event_type", "start_timestamp_from_beginning");

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager databaseManager;
    private final RecordingFormatRegistry recordingFormats;
    private final EventWriter.Factory eventWriterFactory;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileDataInitializer profileDataInitializer;
    private final Clock clock;

    public ProfileInitializerImpl(
            ProfileRepositories profileRepositories,
            DatabaseManager databaseManager,
            RecordingFormatRegistry recordingFormats,
            EventWriter.Factory eventWriterFactory,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            Clock clock) {

        this.profileRepositories = profileRepositories;
        this.databaseManager = databaseManager;
        this.recordingFormats = recordingFormats;
        this.eventWriterFactory = eventWriterFactory;
        this.profileManagerFactory = profileManagerFactory;
        this.profileDataInitializer = profileDataInitializer;
        this.clock = clock;
    }

    @Override
    public ProfileManager initialize(ProfileInfo profileInfo, String recordingId, Path recordingPath) {
        LOG.debug("Initializing profile: profileId={} recordingId={}", profileInfo.id(), recordingId);
        Instant startedAt = clock.instant();
        long initSpan = Spans.start();

        // Open database connection for the new profile (creating the database file on disk if it
        // does not exist yet). The lease keeps this profile's pool pinned for the whole
        // initialization, so a concurrent initialization of a different profile cannot idle-evict or
        // otherwise close the pool while parsing is still writing events into it.
        try (DatabaseLease lease = databaseManager.acquire(profileInfo.id())) {
            DataSource dataSource = lease.dataSource();
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
            // The profiling start is the zero point of the relative event timeline persisted with every event
            EventWriter eventWriter = eventWriterFactory.create(dataSource, profileInfo.profilingStartedAt());
            RecordingEventParser recordingEventParser = recordingFormats.bySource(profileInfo.eventSource()).eventParser();
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

            DatabaseClient infrastructureClient = profileRepositories.databaseClientProvider(dataSource)
                    .provide(GroupLabel.INFRASTRUCTURE);

            // Re-cluster the events table by (event_type, time) once all writers are done. Row-group
            // zone maps then prune scans by event type and time range — replacing the ART indexes.
            Duration clusteringElapsed = Measuring.r(
                    () -> infrastructureClient.recreateTableClustered(EVENTS_TABLE, EVENTS_CLUSTERING_COLUMNS));
            LOG.debug("Events table re-clustered: profile_id={} duration_in_ms={}",
                    profileInfo.id(), clusteringElapsed.toMillis());

            // Ensure all data is flushed to disk - especially important for WAL mode databases
            // WAL checkpointing merges the WAL (Write-Ahead Log) into the main database file
            infrastructureClient.walCheckpoint();

            long elapsedMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli();
            LOG.info("Profile parsed and initialized: profile_id={} profile_name={} elapsed_ms={}",
                    profileInfo.id(), profileInfo.name(), elapsedMs);

            return profileManager;
        } finally {
            // The lease (try-with-resources) releases the pin; the cached pool stays warm for
            // subsequent reads and is closed later by idle eviction, not here.
            Spans.end(initSpan, "profile.initialize");
        }
    }
}
