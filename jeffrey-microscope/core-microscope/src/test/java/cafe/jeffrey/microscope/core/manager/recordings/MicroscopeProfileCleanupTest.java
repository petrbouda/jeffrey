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

package cafe.jeffrey.microscope.core.manager.recordings;

import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.storage.recording.api.file.Recording;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicroscopeProfileCleanupTest {

    private static final String RECORDING_ID = "recording-1";
    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");

    @Mock
    MicroscopeJeffreyDirs jeffreyDirs;

    @Mock
    MicroscopeCoreRepositories repositories;

    @Mock
    ProfileRepository profileRepository;

    @Mock
    Consumer<String> contextInvalidator;

    @TempDir
    Path tempDir;

    @Test
    void invalidatesBeforeAndAfterDeletingProfileStorage() {
        when(jeffreyDirs.profileDir("p-1")).thenReturn(tempDir.resolve("p-1"));
        when(repositories.newProfileRepository("p-1")).thenReturn(profileRepository);
        MicroscopeProfileCleanup cleanup =
                new MicroscopeProfileCleanup(jeffreyDirs, repositories, contextInvalidator);

        cleanup.deleteProfile("p-1");

        InOrder deletion = inOrder(contextInvalidator, profileRepository);
        deletion.verify(contextInvalidator).accept("p-1");
        deletion.verify(profileRepository).delete();
        deletion.verify(contextInvalidator).accept("p-1");
    }

    @Test
    void deletingTheRecordingDeletesEveryProfileBuiltFromIt() {
        ProfileRepository olderProfile = mock(ProfileRepository.class);
        when(jeffreyDirs.profileDir(anyString())).thenAnswer(invocation -> tempDir.resolve((String) invocation.getArgument(0)));
        when(repositories.findProfilesByRecording(RECORDING_ID))
                .thenReturn(List.of(profile("p-2"), profile("p-1")));
        when(repositories.newProfileRepository("p-2")).thenReturn(profileRepository);
        when(repositories.newProfileRepository("p-1")).thenReturn(olderProfile);
        MicroscopeProfileCleanup cleanup =
                new MicroscopeProfileCleanup(jeffreyDirs, repositories, contextInvalidator);

        cleanup.onRecordingDeleted(new Recording(
                RECORDING_ID, "app.jfr", null, RecordingEventSource.JDK, NOW, NOW, NOW,
                true, "p-2", "app.jfr #2", List.of()));

        verify(profileRepository).delete();
        verify(olderProfile).delete();
    }

    private static ProfileInfo profile(String profileId) {
        return new ProfileInfo(profileId, null, null, "app.jfr", RecordingEventSource.JDK,
                NOW, NOW, NOW, true, false, RECORDING_ID);
    }
}
