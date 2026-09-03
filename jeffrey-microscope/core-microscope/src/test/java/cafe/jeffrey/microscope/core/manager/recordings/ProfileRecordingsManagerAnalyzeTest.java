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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import cafe.jeffrey.microscope.runtime.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.profile.ProfileInitializer;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The ordering that decides whether the recordings list can show an initialization while it runs:
 * the list reaches a run's progress through the recording's profile, so the profile row has to
 * exist before the pipeline starts, not after it finishes.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileRecordingsManagerAnalyzeTest {

    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String RECORDING_ID = "recording-1";
    private static final String FILENAME = "app.jfr";

    @Mock
    private RecordingsCoreManager core;
    @Mock
    private MicroscopeJeffreyDirs jeffreyDirs;
    @Mock
    private RecordingInformationParser recordingInformationParser;
    @Mock
    private ProfileInitializer profileInitializer;
    @Mock
    private ProfileManager.Factory profileManagerFactory;
    @Mock
    private MicroscopeCoreRepositories localCoreRepositories;
    @Mock
    private MicroscopeProfileCleanup profileCleanup;
    @Mock
    private RecordingRepository recordingRepository;
    @Mock
    private ProfileRepository profileRepository;

    @TempDir
    private Path recordingsDir;

    private ProfileRecordingsManager manager;

    @BeforeEach
    void setUp() throws IOException {
        Files.createFile(recordingsDir.resolve(RECORDING_ID + "-" + FILENAME));

        when(localCoreRepositories.newRecordingRepository(null)).thenReturn(recordingRepository);
        when(localCoreRepositories.newProfileRepository(any())).thenReturn(profileRepository);
        when(recordingRepository.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording()));

        manager = new ProfileRecordingsManager(
                core,
                FIXED_CLOCK,
                jeffreyDirs,
                recordingsDir,
                recordingInformationParser,
                profileInitializer,
                profileManagerFactory,
                localCoreRepositories,
                profileCleanup);
    }

    private static Recording recording() {
        RecordingFile file = new RecordingFile(
                "file-1", RECORDING_ID, FILENAME, SupportedRecordingFile.JFR, NOW, 1024L);

        return new Recording(
                RECORDING_ID, FILENAME, null, null,
                RecordingEventSource.JDK,
                NOW,
                NOW.minusSeconds(60), NOW,
                false, null, null,
                List.of(file));
    }

    @Nested
    class SuccessfulAnalysis {

        @Test
        void insertsTheProfileRowBeforeInitializingIt() {
            manager.analyzeRecording(RECORDING_ID);

            var order = inOrder(profileRepository, profileInitializer);
            order.verify(profileRepository).insert(any());
            order.verify(profileInitializer).initialize(any(), any(), any());
            order.verify(profileRepository).enableProfile(any());
        }

        @Test
        void returnsTheProfileTheRowWasCreatedFor() {
            String profileId = manager.analyzeRecording(RECORDING_ID);

            verify(localCoreRepositories).newProfileRepository(profileId);
        }
    }

    @Nested
    class FailedAnalysis {

        @Test
        void dropsTheProfileRowAgainSoTheRecordingDoesNotLookAnalyzed() {
            RuntimeException failure = new RuntimeException("parse failed");
            when(profileInitializer.initialize(any(), any(), any())).thenThrow(failure);

            RuntimeException thrown = assertThrows(
                    RuntimeException.class, () -> manager.analyzeRecording(RECORDING_ID));

            assertEquals(failure, thrown);
            verify(profileRepository).delete();
            verify(profileRepository, never()).enableProfile(any());
        }
    }
}
