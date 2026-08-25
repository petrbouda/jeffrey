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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.shared.ui.workspace.bridge.ProfileInitProgress;
import java.util.List;
import cafe.jeffrey.shared.ui.workspace.bridge.RecordingProfileInfoProvider;

/**
 * Microscope's {@link RecordingProfileInfoProvider}: resolves the profile size/modified info for a
 * recording that has an associated analysis profile, via the microscope {@link RecordingsManager},
 * and how far its initialization has got, via the pipeline registry those runs are tracked in.
 * <p>
 * Progress is read from the in-memory registry only, never from the persisted {@code pipeline_runs}
 * row. That row lives in the profile's own database, so consulting it would mean opening every
 * listed profile's database on every request — and it would answer a question the list is not
 * asking. What the list shows is a profile being built right now; a run that ended before the
 * process restarted is not one.
 */
public class MicroscopeRecordingProfileInfoProvider implements RecordingProfileInfoProvider {

    private final RecordingsManager recordingsManager;
    private final PipelineRunRegistry<String> initRunRegistry;

    public MicroscopeRecordingProfileInfoProvider(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> initRunRegistry) {

        this.recordingsManager = recordingsManager;
        this.initRunRegistry = initRunRegistry;
    }

    @Override
    public ProfileInfo profileInfo(Recording recording) {
        if (!recording.hasProfile()) {
            return ProfileInfo.NONE;
        }
        ProfileInitProgress initProgress = initProgress(recording.profileId());

        ProfileManager profileManager = recordingsManager.profile(recording.profileId()).orElse(null);
        if (profileManager == null) {
            // The profile row exists but nothing can be read from it yet -- the state a recording is
            // in while its very first stages run. The progress is still worth reporting: it is the
            // only thing that distinguishes this from a profile that failed to build at all.
            return new ProfileInfo(0L, false, 0L, initProgress);
        }
        return new ProfileInfo(
                profileManager.sizeInBytes(),
                profileManager.info().modified(),
                profileManager.info().createdAt().toEpochMilli(),
                initProgress);
    }

    private ProfileInitProgress initProgress(String profileId) {
        PipelineProgress progress = initRunRegistry.progress(profileId);
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
