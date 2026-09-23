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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.profile.common.pipeline.PipelineDefinition;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.shared.ui.hub.bridge.ProfileInitProgress;
import cafe.jeffrey.shared.ui.hub.bridge.RecordingProfileInfoProvider.ProfileInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicroscopeRecordingProfileInfoProviderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    private static final String PROFILE_ID = "profile-1";

    private static final PipelineDefinition DEFINITION =
            new PipelineDefinition("profile-init", List.of("parse", "flush"));

    private final PipelineRunRegistry<String> registry =
            new PipelineRunRegistry<>(DEFINITION, PipelineRunOptions.unbounded(), CLOCK);

    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);

    private MicroscopeRecordingProfileInfoProvider provider() {
        return new MicroscopeRecordingProfileInfoProvider(recordingsManager, registry);
    }

    private static Recording recordingWithProfile() {
        Recording recording = mock(Recording.class);
        when(recording.hasProfile()).thenReturn(true);
        when(recording.profileId()).thenReturn(PROFILE_ID);
        return recording;
    }

    @Test
    @DisplayName("a recording with no profile reports nothing to show")
    void recordingWithoutProfileHasNoProgress() {
        Recording recording = mock(Recording.class);
        when(recording.hasProfile()).thenReturn(false);

        ProfileInfo info = provider().profileInfo(recording);

        assertEquals(ProfileInfo.NONE, info);
        assertNull(info.initProgress().state());
    }

    @Test
    @DisplayName("a profile that was never initialized in this process reports no run")
    void profileWithoutRunHasNoProgress() {
        when(recordingsManager.profile(PROFILE_ID)).thenReturn(Optional.empty());

        ProfileInfo info = provider().profileInfo(recordingWithProfile());

        assertNull(info.initProgress().state());
        assertTrue(info.initProgress().stages().isEmpty());
    }

    /**
     * The state the whole feature exists for: a profile whose row is there but whose database
     * cannot be read yet, because initialization is still on its first stages. Before this, the list
     * had nothing at all to say about it.
     */
    @Test
    @DisplayName("a profile still initializing reports its stages")
    void profileBeingInitializedReportsStages() {
        when(recordingsManager.profile(PROFILE_ID)).thenReturn(Optional.empty());
        registry.runInline(PipelineRunRequest.of(PROFILE_ID, run -> {
            run.runStage("parse", () -> {
            });
            run.skipStage("flush");
        }));

        ProfileInitProgress progress = provider().profileInfo(recordingWithProfile()).initProgress();

        assertEquals("completed", progress.state());
        assertEquals(2, progress.stages().size());
        assertEquals("parse", progress.stages().getFirst().id());
        assertEquals("completed", progress.stages().getFirst().status());
        assertEquals("skipped", progress.stages().get(1).status());
    }

    @Test
    @DisplayName("a failed initialization is reported as failed rather than as absent")
    void failedInitializationIsReported() {
        when(recordingsManager.profile(PROFILE_ID)).thenReturn(Optional.empty());
        try {
            registry.runInline(PipelineRunRequest.of(PROFILE_ID, run ->
                    run.runStage("parse", () -> {
                        throw new IllegalStateException("recording is corrupt");
                    })));
        } catch (IllegalStateException expected) {
            // The caller sees the failure; what matters here is what the list reports afterwards.
        }

        ProfileInitProgress progress = provider().profileInfo(recordingWithProfile()).initProgress();

        assertEquals("failed", progress.state());
        assertEquals("failed", progress.stages().getFirst().status());
    }
}
