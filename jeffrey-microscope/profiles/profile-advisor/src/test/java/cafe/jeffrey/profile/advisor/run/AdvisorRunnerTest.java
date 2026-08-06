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

package cafe.jeffrey.profile.advisor.run;

import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.profile.advisor.settings.AdvisorSettings;
import cafe.jeffrey.shared.persistence.DatabaseLease;
import cafe.jeffrey.profile.advisor.source.RecordingCommitResolver;
import cafe.jeffrey.profile.advisor.source.SourceTreeResolver;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The source folder is the batch's precondition rather than one type's step, so it is resolved at the
 * launch boundary. What that has to guarantee is here: an unusable folder refuses the launch outright,
 * instead of starting a batch whose every type fails the same way several seconds later.
 */
class AdvisorRunnerTest {

    private static final String PROFILE_ID = "p1";
    private static final String RECORDING_ID = "recording-1";
    private static final String MISSING_FOLDER = "/no/such/folder";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-01T06:00:00Z"), ZoneOffset.UTC);

    private static final List<AdvisorTarget> FOUR = List.of(
            new AdvisorTarget(PROFILE_ID, "jdk.ExecutionSample"),
            new AdvisorTarget(PROFILE_ID, "profiler.WallClockSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.ObjectAllocationSample"),
            new AdvisorTarget(PROFILE_ID, "jdk.JavaMonitorEnter"));

    private static final ProfileInfo PROFILE = new ProfileInfo(
            PROFILE_ID, "project-1", "workspace-1", "profile",
            RecordingEventSource.ASYNC_PROFILER,
            Instant.parse("2026-08-01T05:00:00Z"), Instant.parse("2026-08-01T05:10:00Z"),
            Instant.parse("2026-08-01T05:10:00Z"), true, false, RECORDING_ID);

    /** The folder decides the outcome here, so the recording carries no commit tags. */
    private record StubTagsRepository() implements RecordingTagsRepository {

        @Override
        public void insert(String recordingId, Map<String, String> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingTag> listForRecording(String recordingId) {
            return List.of();
        }

        @Override
        public Map<String, List<RecordingTag>> listForRecordings(Collection<String> recordingIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteForRecording(String recordingId) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Only the collaborators {@code resolveSource()} touches are real. The rest of the pipeline is
     * never reached, which is the point: a batch that cannot resolve its folder schedules nothing.
     */
    private static AdvisorService serviceFor(String sourcePath) {
        return serviceFor(sourcePath, DatabaseLease.unmanaged(null));
    }

    private static AdvisorService serviceFor(String sourcePath, DatabaseLease lease) {
        SourceTreeResolver resolver =
                new SourceTreeResolver(new RecordingCommitResolver(new StubTagsRepository()));

        return new AdvisorService(
                null, new AdvisorSettings(sourcePath, AdvisorSettings.DEFAULT_PRUNE_THRESHOLD_PCT),
                resolver, null, null, null, null, lease, RECORDING_ID, CLOCK);
    }

    @Test
    void refusesTheLaunchWhenTheSourceFolderIsUnusableAndRemembersNoBatch() {
        AdvisorRunner runner = new AdvisorRunner(
                _ -> serviceFor(MISSING_FOLDER),
                (_, _) -> {
                },
                2, CLOCK);

        Exception e = assertThrows(RuntimeException.class, () -> runner.startBatch(PROFILE, FOUR));
        assertTrue(e.getMessage().contains(MISSING_FOLDER),
                "the message must name the folder, since fixing it is the user's next action");

        // A batch left behind here would report as failed for a run that never started, and would block
        // the retry the user is about to make.
        assertNull(runner.batchProgress(PROFILE_ID).status(), "no batch may survive a refused launch");
    }

    /**
     * The service pins the profile's connection pool for the duration of a run, so that a model call
     * lasting minutes cannot have the pool idle-evicted out from under the store that ends the run. A
     * pin that outlives the run is the other half of that bargain: it would keep the pool alive for the
     * life of the process. The refused launch is the sharpest case — the service is built and then
     * discarded on an exception path.
     */
    @Test
    void releasesTheProfilesDatabasePinEvenWhenTheLaunchIsRefused() {
        AtomicBoolean released = new AtomicBoolean(false);
        DatabaseLease lease = new DatabaseLease(null, () -> released.set(true));

        AdvisorRunner runner = new AdvisorRunner(
                _ -> serviceFor(MISSING_FOLDER, lease),
                (_, _) -> {
                },
                2, CLOCK);

        assertThrows(RuntimeException.class, () -> runner.startBatch(PROFILE, FOUR));

        assertTrue(released.get(), "a refused launch must not leave the profile's pool pinned");
    }
}
