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
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.shared.ui.remote.bridge.ProfileInitProgress;
import java.util.List;
import cafe.jeffrey.shared.ui.remote.bridge.RecordingProfileInfoProvider;

/**
 * Microscope's {@link RecordingProfileInfoProvider}: resolves the size/modified info of every profile
 * built from a recording, via the microscope {@link RecordingsManager}, and how far each one's
 * initialization has got, from the pipeline of the recording's kind
 * ({@link RecordingsManager#initProgress}). The stage ids differ by kind, and the frontend groups each
 * set into its own chips.
 * <p>
 * Progress is read from the in-memory registry only, never from the persisted {@code pipeline_runs}
 * row. That row lives in the profile's own database, so consulting it would mean opening every
 * listed profile's database on every request — and it would answer a question the list is not
 * asking. What the list shows is a profile being built right now; a run that ended before the
 * process restarted is not one.
 */
public class MicroscopeRecordingProfileInfoProvider implements RecordingProfileInfoProvider {

    private final RecordingsManager recordingsManager;

    public MicroscopeRecordingProfileInfoProvider(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @Override
    public List<RecordingProfile> profiles(Recording recording) {
        if (!recording.hasProfile()) {
            return List.of();
        }
        return recordingsManager.profiles(recording.id()).stream()
                .map(profile -> recordingProfile(recording, profile))
                .toList();
    }

    private RecordingProfile recordingProfile(Recording recording, ProfileInfo profile) {
        ProfileInitProgress initProgress = initProgress(recordingsManager.initProgress(recording, profile.id()));

        ProfileManager profileManager = recordingsManager.profile(profile.id()).orElse(null);
        if (profileManager == null) {
            // The profile row exists but nothing can be read from it yet -- the state a recording is
            // in while its very first stages run. The progress is still worth reporting: it is the
            // only thing that distinguishes this from a profile that failed to build at all.
            return new RecordingProfile(profile.id(), profile.name(), 0L, false, 0L, initProgress);
        }
        return new RecordingProfile(
                profile.id(),
                profileManager.info().name(),
                profileManager.sizeInBytes(),
                profileManager.info().modified(),
                profileManager.info().createdAt().toEpochMilli(),
                initProgress);
    }

    private static ProfileInitProgress initProgress(PipelineProgress progress) {
        if (progress.state() == PipelineState.IDLE) {
            return ProfileInitProgress.NONE;
        }

        List<ProfileInitProgress.Stage> stages = progress.stages().stream()
                .map(stage -> new ProfileInitProgress.Stage(
                        stage.id(),
                        stage.status().code(),
                        stage.durationMs(),
                        stage.elapsedMs()))
                .toList();

        return new ProfileInitProgress(progress.state().code(), stages);
    }
}
