/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.recordings.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.recordings.MicroscopeProfileCleanup;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.perfcounters.PerfCounterValues;
import cafe.jeffrey.profile.perfcounters.PerfCountersManager;
import cafe.jeffrey.profile.perfcounters.PerfCountersOverviewBuilder;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfCountersProfileBuilderTest {

    private static final Instant NOW = Instant.parse("2026-10-24T08:00:00Z");
    private static final String RECORDING_ID = "rec-perf";
    private static final String FILENAME = "perf-counters.hsperfdata";
    private static final long JVM_START = 1_792_829_252_701L;
    private static final long SECOND = 1_000_000_000L;

    @TempDir
    Path home;

    @Mock
    MicroscopeCoreRepositories repositories;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    ProfileManager.Factory profileManagerFactory;

    @Mock
    ProfileManager profileManager;

    @Mock
    PerfCountersManager perfCountersManager;

    @Mock
    MicroscopeProfileCleanup profileCleanup;

    private MicroscopeJeffreyDirs jeffreyDirs;
    private Path recordingsDir;
    private PerfCountersProfileBuilder builder;

    @BeforeEach
    void setUp() {
        jeffreyDirs = new MicroscopeJeffreyDirs(home);
        recordingsDir = home.resolve("recordings");
        builder = new PerfCountersProfileBuilder(
                Clock.fixed(NOW, ZoneOffset.UTC), jeffreyDirs, recordingsDir, repositories,
                profileManagerFactory, profileCleanup);
    }

    private static Recording snapshot(String profileId) {
        return new Recording(RECORDING_ID, FILENAME, null, RecordingEventSource.PERF_COUNTERS, NOW, null, null,
                profileId != null, profileId, null,
                List.of(new RecordingFile("file-1", RECORDING_ID, FILENAME, ManagedFile.PERF_COUNTERS, NOW, 32_768L)));
    }

    @Nested
    class Build {

        @Test
        void buildsAnEnabledProfileOverTheJvmsRun() {
            when(profileManagerFactory.apply(any())).thenReturn(profileManager);
            when(profileManager.perfCountersManager()).thenReturn(perfCountersManager);
            when(perfCountersManager.ingest(any())).thenReturn(PerfCountersOverviewBuilder.build(PerfCounterValues.of(
                    Map.<String, Object>of(
                            "sun.rt.createVmBeginTime", JVM_START,
                            "sun.rt.applicationTime", 90 * SECOND,
                            "sun.rt.safepointTime", 10 * SECOND))));
            when(repositories.newProfileRepository(anyString())).thenReturn(profileRepository);

            String profileId = builder.build(snapshot(null), FILENAME);

            verify(perfCountersManager).ingest(RecordingStorageLayout.storagePath(recordingsDir, RECORDING_ID, FILENAME));
            ArgumentCaptor<ProfileRepository.InsertProfile> insert = ArgumentCaptor.forClass(ProfileRepository.InsertProfile.class);
            verify(profileRepository).insert(insert.capture());
            assertEquals(RecordingEventSource.PERF_COUNTERS, insert.getValue().eventSource());
            assertEquals(RECORDING_ID, insert.getValue().recordingId());
            assertEquals(Instant.ofEpochMilli(JVM_START), insert.getValue().recordingStartedAt());
            assertEquals(Instant.ofEpochMilli(JVM_START + 100_000), insert.getValue().recordingFinishedAt());
            verify(profileRepository).enableProfile(NOW);
            verify(repositories).newProfileRepository(profileId);
        }

        @Test
        void aSnapshotThatCannotBeReadLeavesNoProfileBehind() {
            ArgumentCaptor<ProfileInfo> started = ArgumentCaptor.forClass(ProfileInfo.class);
            when(profileManagerFactory.apply(started.capture())).thenReturn(profileManager);
            when(profileManager.perfCountersManager()).thenReturn(perfCountersManager);
            when(perfCountersManager.ingest(any())).thenAnswer(_ -> {
                // Opening the profile's database is what creates its directory.
                Files.createDirectories(jeffreyDirs.profileDir(started.getValue().id()));
                throw new IllegalArgumentException("Not a perf-data file");
            });

            assertThrows(IllegalArgumentException.class, () -> builder.build(snapshot(null), FILENAME));

            assertFalse(Files.exists(jeffreyDirs.profileDir(started.getValue().id())));
            verify(repositories, never()).newProfileRepository(anyString());
            verify(profileCleanup, never()).deleteProfile(anyString());
        }
    }

    @Nested
    class Reuse {

        private static ProfileInfo profile(boolean enabled) {
            return new ProfileInfo("p-1", null, null, FILENAME, RecordingEventSource.PERF_COUNTERS,
                    null, null, NOW, enabled, false, RECORDING_ID);
        }

        @Test
        void keepsAProfileThatHoldsItsCounters() {
            when(repositories.newProfileRepository("p-1")).thenReturn(profileRepository);
            when(profileRepository.find()).thenReturn(Optional.of(profile(true)));
            when(profileManagerFactory.apply(any())).thenReturn(profileManager);
            when(profileManager.perfCountersManager()).thenReturn(perfCountersManager);
            when(perfCountersManager.exists()).thenReturn(true);

            assertEquals(Optional.of("p-1"), builder.reuse(snapshot("p-1")));
            verify(profileCleanup, never()).deleteProfile(anyString());
        }

        @Test
        void dropsAProfileThatLostItsCounters() {
            when(repositories.newProfileRepository("p-1")).thenReturn(profileRepository);
            when(profileRepository.find()).thenReturn(Optional.of(profile(true)));
            when(profileManagerFactory.apply(any())).thenReturn(profileManager);
            when(profileManager.perfCountersManager()).thenReturn(perfCountersManager);
            when(perfCountersManager.exists()).thenReturn(false);

            assertTrue(builder.reuse(snapshot("p-1")).isEmpty());
            verify(profileCleanup).deleteProfile("p-1");
        }
    }

    /** Reading a snapshot takes milliseconds within the request: there are no stages to follow. */
    @Test
    void hasNoStagesToReport() {
        assertEquals(PipelineState.IDLE, builder.progress("p-1").state());
        assertTrue(builder.progress("p-1").stages().isEmpty());
    }
}
