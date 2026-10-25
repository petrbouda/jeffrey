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
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.Optional;

/**
 * Resolves a {@code profileId} to the underlying {@link ProfileManager}.
 * Checks the Recordings store first, then falls back to a direct DB
 * lookup against the microscope-core profile repository.
 *
 * <p>For workspace-scoped profiles, asks the configured remote, the only
 * place such a profile can come from.
 */
public class ProfileManagerResolver {

    private final RemoteManager remoteManager;
    private final RecordingsManager recordingsManager;
    private final MicroscopeCoreRepositories localCoreRepositories;

    public ProfileManagerResolver(
            RemoteManager remoteManager,
            RecordingsManager recordingsManager,
            MicroscopeCoreRepositories localCoreRepositories) {
        this.remoteManager = remoteManager;
        this.recordingsManager = recordingsManager;
        this.localCoreRepositories = localCoreRepositories;
    }

    public ProfileManager resolve(String profileId) {
        return find(profileId)
                .orElseThrow(() -> Exceptions.profileNotFound(profileId));
    }

    public Optional<ProfileManager> find(String profileId) {
        if (recordingsManager != null) {
            Optional<ProfileManager> quickProfile = recordingsManager.profile(profileId);
            if (quickProfile.isPresent()) {
                return quickProfile;
            }
        }

        Optional<ProfileInfo> profileInfoOpt = localCoreRepositories.newProfileRepository(profileId).find();
        if (profileInfoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProfileInfo profileInfo = profileInfoOpt.get();
        return remoteManager.current()
                .flatMap(remote -> remote.project(profileInfo.workspaceId(), profileInfo.projectId()))
                .flatMap(pm -> pm.profilesManager().profile(profileId));
    }
}
