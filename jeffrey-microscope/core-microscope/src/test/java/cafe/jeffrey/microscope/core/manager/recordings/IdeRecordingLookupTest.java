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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.Kind;
import cafe.jeffrey.microscope.core.web.dto.response.IdeRecordingStateResponse.State;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.model.EventSummaryResult.SingleResult;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How a file on the developer's disk is matched to what Microscope holds. The rule is name plus
 * byte size, because an import records no origin path — so this is where being wrong about a file is
 * caught, rather than in a panel that quietly offers to analyse something already analysed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdeRecordingLookupTest {

    private static final Path RECORDING_PATH = Path.of("/home/dev/jeffrey/jeffrey-20260904-180108.jfr");
    private static final String FILENAME = "jeffrey-20260904-180108.jfr";
    private static final long SIZE = 8_450_244L;
    private static final String PROFILE_ID = "profile-1";

    @Mock
    RecordingsManager recordingsManager;

    @Mock
    ProfileManagerResolver profileManagerResolver;

    @Mock
    PipelineRunRegistry<String> profileInitRunRegistry;

    IdeRecordingLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new IdeRecordingLookup(recordingsManager, profileManagerResolver, profileInitRunRegistry);
        when(profileManagerResolver.find(PROFILE_ID)).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("matching a file to a recording")
    class Matching {

        @Test
        void reportsNotImportedWhenNothingMatches() {
            when(recordingsManager.listRecordings()).thenReturn(List.of());

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals(State.NOT_IMPORTED.name(), response.state());
            assertEquals(FILENAME, response.filename());
            assertEquals(SIZE, response.sizeInBytes());
            assertNull(response.recordingId());
            assertNull(response.summary());
        }

        /**
         * The reason the size is part of the rule. Two runs of the same profiled build produce files
         * with the same name in different directories, and matching on the name alone would show the
         * older run's findings against the newer file.
         */
        @Test
        void doesNotMatchTheSameNameAtADifferentSize() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", false, SIZE + 1)));

            assertEquals(State.NOT_IMPORTED.name(), lookup.byPath(RECORDING_PATH, SIZE).state());
        }

        /** A caller that could not stat the file sends 0, and then the name alone has to do. */
        @Test
        void fallsBackToTheNameWhenNoSizeIsGiven() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", false, SIZE)));

            assertEquals(State.IMPORTED.name(), lookup.byPath(RECORDING_PATH, 0).state());
        }

        @Test
        void picksTheNewestImportWhenTheSameFileWasImportedTwice() {
            Recording older = recording("rec-old", false, SIZE, Instant.parse("2026-09-04T18:00:00Z"));
            Recording newer = recording("rec-new", false, SIZE, Instant.parse("2026-09-04T18:30:00Z"));
            when(recordingsManager.listRecordings()).thenReturn(List.of(older, newer));

            assertEquals("rec-new", lookup.byPath(RECORDING_PATH, SIZE).recordingId());
        }

        /**
         * Found by running it: importing the same file a second time without analysing it hid the
         * analysis that already existed, and the panel offered to rebuild what was right there.
         */
        @Test
        void prefersAnAnalysedImportOverANewerBareOne() {
            Recording analysed = recording("rec-analysed", true, SIZE, Instant.parse("2026-09-04T18:00:00Z"));
            Recording bare = recording("rec-bare", false, SIZE, Instant.parse("2026-09-04T18:30:00Z"));
            when(recordingsManager.listRecordings()).thenReturn(List.of(analysed, bare));
            when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(true);

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals("rec-analysed", response.recordingId());
            assertEquals(State.ANALYZING.name(), response.state());
        }
    }

    @Nested
    @DisplayName("reporting the state of a matched recording")
    class Reporting {

        @Test
        void reportsImportedWhenTheRecordingHasNoProfile() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", false, SIZE)));

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals(State.IMPORTED.name(), response.state());
            assertEquals("rec-1", response.recordingId());
            assertNull(response.profileId());
        }

        @Test
        void reportsAnalyzingWhileTheInitPipelineRuns() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", true, SIZE)));
            when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(true);

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals(State.ANALYZING.name(), response.state());
            assertEquals(PROFILE_ID, response.profileId());
            assertNull(response.summary());
        }

        /**
         * A recording can outlive the profile it points at — deleting a profile leaves the recording.
         * Offering to analyse again beats reporting a link that opens nothing.
         */
        @Test
        void fallsBackToImportedWhenTheProfileCannotBeOpened() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", true, SIZE)));
            when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(false);

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals(State.IMPORTED.name(), response.state());
            assertNull(response.profileId());
        }
    }

    @Nested
    @DisplayName("the summary a ready profile carries")
    class Summary {

        @Test
        void carriesTheFiguresThePanelShows() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", true, SIZE)));
            when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(false);
            // Stubbed into a local first: calling readyProfile() inside thenReturn(...) nests one
            // when(...) inside another, which Mockito reports as unfinished stubbing.
            ProfileManager profileManager = readyProfile();
            when(profileManagerResolver.find(PROFILE_ID)).thenReturn(Optional.of(profileManager));

            IdeRecordingStateResponse response = lookup.byPath(RECORDING_PATH, SIZE);

            assertEquals(State.READY.name(), response.state());
            var summary = response.summary();
            assertEquals(Kind.RECORDING, summary.kind());
            assertEquals("jeffrey-20260904-180108", summary.profileName());
            assertNull(summary.heap(), "a recording carries no heap figures");
            assertEquals(42_000L, summary.recording().durationInMillis());
            assertEquals(1_240_000L, summary.recording().sampleCount());
            assertEquals(2, summary.recording().eventTypeCount());
            assertEquals(980_000L, summary.recording().capturedSamples());
            assertEquals(0L, summary.recording().lostSamples());
            assertTrue(summary.analysisComputed());
            assertEquals(1, summary.findings().size());
            assertEquals("WARNING", summary.findings().getFirst().severity());
            // TRACES from the profile itself, HEAP_DUMP because no dump is attached — the panel needs
            // both, since to a reader "no data" and "no heap dump" are the same empty page.
            assertEquals(List.of("HEAP_DUMP", "TRACES"), summary.disabledFeatures());
            assertEquals("Long GC pauses", summary.findings().getFirst().summary());
        }

        @Test
        void reportsAnalysisNotComputedRatherThanNoFindings() {
            when(recordingsManager.listRecordings()).thenReturn(List.of(recording("rec-1", true, SIZE)));
            when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(false);
            ProfileManager profileManager = readyProfile();
            when(profileManager.autoAnalysisManager().analysisResults()).thenReturn(List.of());
            when(profileManagerResolver.find(PROFILE_ID)).thenReturn(Optional.of(profileManager));

            var summary = lookup.byPath(RECORDING_PATH, SIZE).summary();

            assertEquals(false, summary.analysisComputed());
            assertTrue(summary.findings().isEmpty());
        }
    }

    @Nested
    @DisplayName("a heap dump")
    class HeapDumps {

        private static final Path DUMP_PATH = Path.of("/home/dev/jeffrey/app.hprof");

        @Test
        void reportsHeapFiguresInsteadOfRecordingOnes() {
            stubReadyProfile(readyHeapDump(true), "app.hprof");

            var summary = lookup.byPath(DUMP_PATH, SIZE).summary();

            assertEquals(Kind.HEAP_DUMP, summary.kind());
            assertNull(summary.recording(), "a heap dump has no recording window");
            assertEquals(512_000_000L, summary.heap().totalBytes());
            assertEquals(3_400_000L, summary.heap().totalInstances());
            assertEquals(9_100, summary.heap().classCount());
            assertEquals(742, summary.heap().gcRootCount());
            assertTrue(summary.heap().cacheReady());
        }

        /**
         * A dump that has not been indexed can answer nothing, and indexing a large heap is minutes of
         * work. The panel has to say so rather than show four zeroes as if they were facts.
         */
        @Test
        void reportsAnUnindexedDumpAsNotReadyRatherThanAsZeroes() {
            stubReadyProfile(readyHeapDump(false), "app.hprof");

            var heap = lookup.byPath(DUMP_PATH, SIZE).summary().heap();

            assertFalse(heap.cacheReady());
            assertEquals(0L, heap.totalBytes());
        }

        @Test
        void recognisesACompressedDump() {
            stubReadyProfile(readyHeapDump(true), "app.hprof.gz");

            assertEquals(
                    Kind.HEAP_DUMP,
                    lookup.byPath(Path.of("/home/dev/jeffrey/app.hprof.gz"), SIZE).summary().kind());
        }

        /**
         * What was double-clicked decides the kind. A recording with a heap dump attached still shows
         * the recording's figures when the .jfr is what was opened.
         */
        @Test
        void readsAJfrAsARecordingEvenWhenItCarriesAHeapDump() {
            ProfileManager profileManager = readyProfile();
            when(profileManager.heapDumpManager().heapDumpExists()).thenReturn(true);
            when(profileManager.heapDumpManager().isCacheReady()).thenReturn(true);
            stubReadyProfile(profileManager, FILENAME);

            var summary = lookup.byPath(RECORDING_PATH, SIZE).summary();

            assertEquals(Kind.RECORDING, summary.kind());
            assertNotNull(summary.recording());
            assertNull(summary.heap());
        }
    }

    /** Points the lookup at a matching recording whose profile is ready, under the given file name. */
    private void stubReadyProfile(ProfileManager profileManager, String filename) {
        when(recordingsManager.listRecordings()).thenReturn(List.of(recordingNamed(filename)));
        when(profileInitRunRegistry.isRunning(PROFILE_ID)).thenReturn(false);
        when(profileManagerResolver.find(PROFILE_ID)).thenReturn(Optional.of(profileManager));
    }

    private static ProfileManager readyHeapDump(boolean cacheReady) {
        ProfileManager profileManager = readyProfile();
        when(profileManager.heapDumpManager().heapDumpExists()).thenReturn(true);
        when(profileManager.heapDumpManager().isCacheReady()).thenReturn(cacheReady);
        when(profileManager.heapDumpManager().getSummary()).thenReturn(new HeapSummary(
                512_000_000L, 3_400_000L, 9_100, 742, Instant.parse("2026-09-04T18:01:00Z")));
        return profileManager;
    }

    private static Recording recordingNamed(String filename) {
        return new Recording(
                "rec-1", filename, null, null, RecordingEventSource.JDK,
                Instant.parse("2026-09-04T18:02:00Z"),
                Instant.parse("2026-09-04T18:01:00Z"),
                Instant.parse("2026-09-04T18:01:42Z"),
                true, PROFILE_ID, "profile",
                List.of(new RecordingFile("file-1", "rec-1", filename, SupportedRecordingFile.JFR,
                        Instant.parse("2026-09-04T18:02:00Z"), SIZE)));
    }

    private static ProfileManager readyProfile() {
        ProfileManager profileManager = mock(ProfileManager.class, RETURNS_DEEP_STUBS);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID,
                null,
                null,
                "jeffrey-20260904-180108",
                RecordingEventSource.JDK,
                Instant.parse("2026-09-04T18:01:00Z"),
                Instant.parse("2026-09-04T18:01:42Z"),
                Instant.parse("2026-09-04T18:02:00Z"),
                true,
                false,
                "rec-1"));
        when(profileManager.samplerHealthManager().cpuTimeSampleLoss())
                .thenReturn(new CpuTimeSampleLoss(980_000L, 0L, 0L));
        when(profileManager.autoAnalysisManager().analysisResults()).thenReturn(List.of(
                new AutoAnalysisResult(
                        "gc-pauses", Severity.WARNING, "3 pauses above 200 ms",
                        "Long GC pauses", "Tune the collector", "341")));
        // Deep stubs answer null for an unstubbed List, and the disabled-features scan reads one.
        when(profileManager.featuresManager().getDisabledFeatures()).thenReturn(List.of(FeatureType.TRACES));
        when(profileManager.heapDumpManager().heapDumpExists()).thenReturn(false);
        when(profileManager.flamegraphManager().allEventSummaries()).thenReturn(List.of(
                eventSummary("jdk.ExecutionSample", 1_000_000L),
                eventSummary("jdk.ObjectAllocationSample", 240_000L)));
        return profileManager;
    }

    private static EventSummaryResult eventSummary(String code, long samples) {
        return new EventSummaryResult(
                code, code, new SingleResult(code, code, null, null, samples, 0L, false, Map.of()), null);
    }

    private static Recording recording(String id, boolean hasProfile, long sizeInBytes) {
        return recording(id, hasProfile, sizeInBytes, Instant.parse("2026-09-04T18:02:00Z"));
    }

    private static Recording recording(String id, boolean hasProfile, long sizeInBytes, Instant createdAt) {
        return new Recording(
                id,
                FILENAME,
                null,
                null,
                RecordingEventSource.JDK,
                createdAt,
                Instant.parse("2026-09-04T18:01:00Z"),
                Instant.parse("2026-09-04T18:01:42Z"),
                hasProfile,
                hasProfile ? PROFILE_ID : null,
                hasProfile ? "jeffrey-20260904-180108" : null,
                List.of(new RecordingFile(
                        "file-1", id, FILENAME, SupportedRecordingFile.JFR,
                        Instant.parse("2026-09-04T18:02:00Z"), sizeInBytes)));
    }
}
