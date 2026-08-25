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
import cafe.jeffrey.profile.common.pipeline.PipelineRun;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.manager.action.ProfileDataInitializer;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingEventParser;
import cafe.jeffrey.provider.profile.api.RecordingEventParserResolver;
import cafe.jeffrey.provider.profile.api.ProfileInfoRepository;
import cafe.jeffrey.provider.profile.api.ProfileRepositories;
import cafe.jeffrey.provider.profile.api.TraceAttributeRepository;
import cafe.jeffrey.provider.profile.api.TraceRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.shared.persistence.DatabaseManager;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ProfileInitializerImpl implements ProfileInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileInitializerImpl.class);

    // The physical table behind the `events` view -- the CTAS re-cluster swaps the table
    // underneath the view, which re-binds by name on the next read.
    private static final String EVENTS_TABLE = "events_raw";


    /**
     * Clustering keys of the events table: queries always filter by event type and very often by a
     * relative time range, so ordering row groups by (event_type, time) gives both predicates tight
     * zone maps.
     */
    private static final List<String> EVENTS_CLUSTERING_COLUMNS =
            List.of("event_type", "start_timestamp_from_beginning");

    private final ProfileRepositories profileRepositories;
    private final DatabaseManager databaseManager;
    private final RecordingEventParserResolver recordingEventParserResolver;
    private final EventWriter.Factory eventWriterFactory;
    private final ProfileManager.Factory profileManagerFactory;
    private final ProfileDataInitializer profileDataInitializer;
    private final PipelineRunRegistry<String> runRegistry;
    private final Clock clock;

    public ProfileInitializerImpl(
            ProfileRepositories profileRepositories,
            DatabaseManager databaseManager,
            RecordingEventParserResolver recordingEventParserResolver,
            EventWriter.Factory eventWriterFactory,
            ProfileManager.Factory profileManagerFactory,
            ProfileDataInitializer profileDataInitializer,
            PipelineRunRegistry<String> runRegistry,
            Clock clock) {

        this.profileRepositories = profileRepositories;
        this.databaseManager = databaseManager;
        this.recordingEventParserResolver = recordingEventParserResolver;
        this.eventWriterFactory = eventWriterFactory;
        this.profileManagerFactory = profileManagerFactory;
        this.profileDataInitializer = profileDataInitializer;
        this.runRegistry = runRegistry;
        this.clock = clock;
    }

    @Override
    public ProfileManager initialize(ProfileInfo profileInfo, String recordingId, Path recordingPath) {
        LOG.debug("Initializing profile: profileId={} recordingId={}", profileInfo.id(), recordingId);
        Instant startedAt = clock.instant();

        // Open database connection for the new profile (creating the database file on disk if it
        // does not exist yet). The lease keeps this profile's pool pinned for the whole
        // initialization, so a concurrent initialization of a different profile cannot idle-evict or
        // otherwise close the pool while parsing is still writing events into it. Closing it releases
        // the pin; the cached pool stays warm for subsequent reads and is closed later by idle
        // eviction, not here.
        try (DatabaseLease lease = databaseManager.acquire(profileInfo.id())) {
            // Runs as a tracked pipeline, the way heap-dump initialization and the Advisor already
            // do. Each step below is a stage, which gives it three things it did not have: a
            // duration recorded against a stable id, a way to say a step was skipped rather than
            // merely fast, and somewhere for a failure to land -- a failed initialization used to
            // leave a profile row disabled forever with nothing anywhere saying why.
            //
            // Inline rather than queued: this method has to return the manager so the caller can
            // enable the profile, so handing the work to the registry's executor would only mean
            // waiting for it again. The registry still tracks the run, so its progress is readable
            // by profile id while it happens.
            AtomicReference<ProfileManager> initialized = new AtomicReference<>();
            runRegistry.runInline(PipelineRunRequest.of(profileInfo.id(),
                    run -> initialized.set(runStages(run, profileInfo, recordingId, recordingPath, lease))));

            long elapsedMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli();
            LOG.info("Profile parsed and initialized: profile_id={} profile_name={} elapsed_ms={}",
                    profileInfo.id(), profileInfo.name(), elapsedMs);

            return initialized.get();
        }
    }

    private ProfileManager runStages(
            PipelineRun run,
            ProfileInfo profileInfo,
            String recordingId,
            Path recordingPath,
            DatabaseLease lease) {

        DataSource dataSource = lease.dataSource();

        // Store profile context (workspace_id, project_id) in the profile database.
        // Skipped for Recordings profiles, where workspace and project are null.
        if (profileInfo.projectId() != null && profileInfo.workspaceId() != null) {
            run.runStage(ProfileInitStages.PROFILE_INFO, () -> {
                ProfileInfoRepository profileInfoRepository =
                        profileRepositories.newProfileInfoRepository(dataSource);
                profileInfoRepository.insert(new ProfileInfoRepository.ProfileContext(
                        profileInfo.id(),
                        profileInfo.projectId(),
                        profileInfo.workspaceId()));
            });
        } else {
            run.skipStage(ProfileInitStages.PROFILE_INFO);
        }

        // Parse recording and store events into the database.
        // The profiling start is the zero point of the relative event timeline persisted with every event.
        EventWriter eventWriter = eventWriterFactory.create(dataSource, profileInfo.profilingStartedAt());
        RecordingEventParser recordingEventParser =
                recordingEventParserResolver.resolve(profileInfo.eventSource());
        run.runStage(ProfileInitStages.PARSE, () -> recordingEventParser.start(eventWriter, recordingPath));
        run.runStage(ProfileInitStages.FLUSH, eventWriter::onComplete);

        DatabaseClient infrastructureClient = profileRepositories.databaseClientProvider(dataSource)
                .provide(GroupLabel.INFRASTRUCTURE);

        // Re-cluster the events table by (event_type, time) as soon as the writers are done.
        // Row-group zone maps then prune scans by event type and time range — replacing the
        // ART indexes. Before the trace derivation on purpose: the derivation scans events
        // by event type several times and profits from the clustering, and the blocks freed
        // by dropping the unclustered copy are reused by the trace tables written next,
        // instead of staying dead space at the end of the file.
        run.runStage(ProfileInitStages.RECLUSTER,
                () -> infrastructureClient.recreateTableClustered(EVENTS_TABLE, EVENTS_CLUSTERING_COLUMNS));

        deriveTraces(run, profileInfo, dataSource);

        ProfileManager profileManager = profileManagerFactory.apply(profileInfo);

        // Process additional files (like logs, metrics, heap-dumps, perf-counters etc.)
        // Currently only perf-counters are supported.
        // Skipped for Recordings, where recordingId is null.
        if (recordingId != null) {
            run.runStage(ProfileInitStages.ADDITIONAL_FILES,
                    () -> profileManager.additionalFilesManager().processAdditionalFiles(recordingId));
        } else {
            run.skipStage(ProfileInitStages.ADDITIONAL_FILES);
        }

        // Ensure all data is flushed to disk - especially important for WAL mode databases.
        // WAL checkpointing merges the WAL (Write-Ahead Log) into the main database file.
        // Before the warming below, not after: the checkpoint wants the writes it is merging
        // to be finished, and the warming only ever writes cache entries it can rebuild.
        run.runStage(ProfileInitStages.CHECKPOINT, infrastructureClient::walCheckpoint);

        // Last, and deliberately not waited for. Everything above leaves the profile queryable --
        // events, traces, event types, threads are all written -- so this is the point the profile
        // is usable, and the caller enables it as soon as we return. The Guardian's frame trees and
        // the thread bands are caches: warming them eagerly is worth doing, but making every user
        // wait for them before they can open a flamegraph is not. The stage therefore measures
        // starting the warming, not finishing it; the warming holds its own lease until it is done.
        run.runStage(ProfileInitStages.WARMUP, () -> profileDataInitializer.initialize(profileManager));

        return profileManager;
    }

    /**
     * Lifts the spans hiding in {@code events} into the typed trace tables, once, while the events
     * are freshly written and before anything can ask for a trace.
     */
    private void deriveTraces(PipelineRun run, ProfileInfo profileInfo, DataSource dataSource) {
        TraceRepository traceRepository = profileRepositories.newTraceRepository(dataSource);

        // A recording that declares no span-carrying event type cannot yield a single derived row:
        // the blocking events the derivation would promote to leaf spans are only ever attached as
        // children of a recorded span on their own thread, so with nothing to parent them to every
        // table comes out empty. Skipping is not just cheaper, it is the same result -- and it costs
        // a scan of every socket, file, monitor and park event in the recording to arrive at
        // otherwise. Reported as skipped rather than as an instant success, so "this recording
        // carries no traces" is something the progress says rather than something you infer.
        if (!traceRepository.hasSpanEventTypes()) {
            LOG.debug("No span-carrying event types, skipping trace derivation: profile_id={}",
                    profileInfo.id());
            run.skipStage(ProfileInitStages.TRACES);
            return;
        }

        run.runStage(ProfileInitStages.TRACES, () -> {
            traceRepository.derive();

            // Then flatten what those spans carry -- their attributes, their event type's own fields
            // and their shape columns -- into the queryable attribute index. Strictly after the
            // spans: it reads trace_spans, so there is nothing to flatten until the statement above
            // has run.
            TraceAttributeRepository attributeRepository =
                    profileRepositories.newTraceAttributeRepository(dataSource);
            attributeRepository.derive();
        });
    }
}
